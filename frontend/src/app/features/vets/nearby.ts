import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiClientService, NearbyVet } from '../../core/api/api-client.service';
import { Coordinates, LocationAccessError, LocationService } from '../../core/location/location.service';

@Component({
  selector: 'app-nearby-vets',
  imports: [],
  templateUrl: './nearby.html',
  styleUrl: './style.css',
})
export class NearbyVets {
  private readonly api = inject(ApiClientService);
  private readonly location = inject(LocationService);

  readonly vets = signal<NearbyVet[]>([]);
  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly error = signal('');
  private searchingVets = false;

  async findNearby(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    try {
      const coordinates = await this.location.getForNearby();
      this.loadVets(coordinates);
    } catch (error) {
      this.loading.set(false);
      if (error instanceof LocationAccessError) {
        this.error.set({
          denied: 'Location access was denied. Allow it in your browser settings to find nearby vets.',
          unavailable: 'Your location is unavailable right now. Please try again.',
          timeout: 'Finding your location timed out. Please try again.',
          unsupported: 'Location access is unavailable. Use HTTPS or localhost and a supported browser.',
        }[error.reason]);
      } else {
        this.error.set('Could not find your location. Please try again.');
      }
    }
  }

  private loadVets(coordinates: Coordinates): void {
    if (this.searchingVets) return;
    this.searchingVets = true;
    this.searched.set(true);
    this.loading.set(true);
    this.error.set('');
    this.vets.set([]);
    this.api.getNearbyVets(28.5892,77.1751).subscribe({
      next: (vets) => {
        this.vets.set(vets);
        this.loading.set(false);
        this.searchingVets = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(error.status === 503 || error.status === 502
          ? 'Clinic search is temporarily unavailable. Please try again later.'
          : 'Could not load nearby clinics. Please try again.');
        this.loading.set(false);
        this.searchingVets = false;
      },
    });
  }
}
