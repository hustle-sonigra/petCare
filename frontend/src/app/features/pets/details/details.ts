import { Component, inject, signal } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink , Router } from '@angular/router';
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
  private router = inject(Router);
  readonly error = signal('');
  readonly deletingIds = signal<string[]>([]);
  readonly deleteError = signal('');
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

  deleteVaccine(recordId:string):void{
    // i have the correct recordId with me , this particular recordId has to be deleted 
    // the important point here is to have this deleted and make sure to again redirect here 
    // on the same page i am currently on . 
    // i pass the parameter vaccine id as it will be useful .
    
    // check if deletion is on for this or not .
   if (this.deletingIds().includes(recordId)) return;
   if (!window.confirm('Delete this vaccination record?')) return;

   // add this particular id in the list that considers the ones to be deleted
  this.deleteError.set('');
  this.deletingIds.update((ids) => [...ids, recordId]);

  this.api.deleteVaccine(recordId).pipe(takeUntilDestroyed(this.destroyRef))
  .subscribe({
    next: () => {
      this.vaccinations.update((records)=>records.filter((record)=>record.id!==recordId),);
      this.certificates.update((states)=>{
        const remaining = {...states};
        delete remaining[recordId];
        return remaining;
      });
      this.deletingIds.update((ids) =>
          ids.filter((id) => id !== recordId),
        );
    },
    error: () => {
        this.deletingIds.update((ids) =>
          ids.filter((id) => id !== recordId),
        );
        this.deleteError.set(
          'Could not delete the vaccination. Please try again.',
        );
    },
  });
  }

  // DB structure : 
  // there has to be an owner 
  // each pet created is linked to an owner
  // each vaccination record has a mention of the pet associated to the vaccine 
  // What we have currently is a vaccine record 
  // we need to move from that to a pet 
  // get the pet from the vaccine record 
  // from that get the 

  editVaccineRecord(recordId: string): void {
    void this.router.navigate(['/pets', this.petId, 'vaccinations', 'update'], {
      queryParams: { vaccinationId: recordId },
    });
  }
}
