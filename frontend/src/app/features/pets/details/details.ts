import { Component, inject, signal } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  ApiClientService,
  PetSummary,
  VaccinationRecord,
  VaccinationCertificate,
} from '../../../core/api/api-client.service';
import { downloadFile } from '../../../shared/utils/download';

interface CertificateState {
  loading: boolean;
  metadata: VaccinationCertificate | null;
  error: string;
  downloading: boolean;
}

@Component({
  selector: 'app-pet-details',
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class PetDetails {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly petId = this.route.snapshot.paramMap.get('petId') ?? '';
  readonly pet = signal<PetSummary | null>(null);
  readonly vaccinations = signal<VaccinationRecord[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly certificates = signal<Partial<Record<string, CertificateState>>>({});

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    if (!this.petId) {
      this.error.set('Pet ID is missing.');
      this.loading.set(false);
      return;
    }
    forkJoin({
      pet: this.api.getPet(this.petId),
      records: this.api.getVaccinations(this.petId),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ pet, records }) => {
          this.pet.set(pet);
          this.vaccinations.set(
            [...records].sort((a, b) => b.administeredOn.localeCompare(a.administeredOn)),
          );
          this.loading.set(false);
          for (const record of records) this.loadCertificate(record.id);
        },
        error: () => {
          this.error.set('Could not load this pet and their records. Please try again.');
          this.loading.set(false);
        },
      });
  }

  loadCertificate(recordId: string): void {
    this.updateCertificate(recordId, {
      loading: true,
      metadata: null,
      error: '',
      downloading: false,
    });
    this.api
      .getVaccinationCertificateMetadata(recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metadata) => this.updateCertificate(recordId, { loading: false, metadata }),
        error: (response: HttpErrorResponse) =>
          this.updateCertificate(recordId, {
            loading: false,
            error:
              response.status === 404 ? '' : 'Could not check the certificate. Please try again.',
          }),
      });
  }

  downloadCertificate(recordId: string): void {
    const state = this.certificates()[recordId];
    if (!state?.metadata || state.downloading) return;
    const filename = state.metadata.originalFilename;
    this.updateCertificate(recordId, { downloading: true, error: '' });
    this.api
      .downloadVaccinationCertificate(recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          downloadFile(blob, filename);
          this.updateCertificate(recordId, { downloading: false });
        },
        error: () =>
          this.updateCertificate(recordId, {
            downloading: false,
            error: 'Could not download the certificate. Please try again.',
          }),
      });
  }

  private updateCertificate(recordId: string, patch: Partial<CertificateState>): void {
    this.certificates.update((states) => ({
      ...states,
      [recordId]: {
        ...(states[recordId] ?? {
          loading: false,
          metadata: null,
          error: '',
          downloading: false,
        }),
        ...patch,
      },
    }));
  }
}
