import { Injectable, signal } from '@angular/core';

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export type LocationFailure = 'denied' | 'unavailable' | 'timeout' | 'unsupported';

export class LocationAccessError extends Error {
  constructor(readonly reason: LocationFailure) {
    super(reason);
  }
}

const LOCATION_REQUESTED_KEY = 'petcare-location-requested';

@Injectable({ providedIn: 'root' })
export class LocationService {
  readonly coordinates = signal<Coordinates | null>(null);
  private requestedThisLoad = false;
  private pending: Promise<Coordinates> | null = null;

  async initialize(): Promise<void> {
    if (this.requestedThisLoad || typeof navigator === 'undefined') return;
    this.requestedThisLoad = true;

    if (!navigator.geolocation || !globalThis.isSecureContext) return;

    // A granted permission can be used again after a reload without prompting.
    // A denied permission, or a prompt already shown in this tab, is left alone.
    let permission: PermissionState | undefined;
    if (navigator.permissions?.query) {
      try {
        permission = (await navigator.permissions.query({ name: 'geolocation' })).state;
      } catch {
        // Some browsers do not support querying geolocation permission.
      }
    }

    if (permission === 'denied') return;
    if (permission !== 'granted' && this.wasRequestedInTab()) return;

    // Dashboard permission failures never affect its data or rendering.
    try {
      await this.requestPosition();
    } catch {
      // The nearby-vets page can offer an explicit retry later.
    }
  }

  getForNearby(): Promise<Coordinates> {
    const saved = this.coordinates();
    return saved ? Promise.resolve(saved) : this.requestPosition();
  }

  private requestPosition(): Promise<Coordinates> {
    if (this.pending) return this.pending;
    if (typeof navigator === 'undefined' || !navigator.geolocation || !globalThis.isSecureContext) {
      return Promise.reject(new LocationAccessError('unsupported'));
    }

    this.markRequestedInTab();
    this.pending = new Promise<Coordinates>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        ({ coords }) => {
          const position = { latitude: coords.latitude, longitude: coords.longitude };
          this.coordinates.set(position);
          resolve(position);
        },
        (error: GeolocationPositionError) => {
          if (error.code === error.PERMISSION_DENIED) this.coordinates.set(null);
          const reason: LocationFailure = error.code === error.PERMISSION_DENIED ? 'denied'
            : error.code === error.TIMEOUT ? 'timeout' : 'unavailable';
          reject(new LocationAccessError(reason));
        },
        { timeout: 10_000, maximumAge: 60_000 },
      );
    });
    void this.pending.finally(() => { this.pending = null; }).catch(() => {});
    return this.pending;
  }

  private wasRequestedInTab(): boolean {
    try {
      return sessionStorage.getItem(LOCATION_REQUESTED_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private markRequestedInTab(): void {
    try {
      sessionStorage.setItem(LOCATION_REQUESTED_KEY, 'true');
    } catch {
      // Storage may be disabled; the service still prevents repeat calls in memory.
    }
  }
}
