import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, finalize, map, of, switchMap } from 'rxjs';
import { ApiClientService } from '../../../core/api/api-client.service';

@Component({
  selector: 'app-update-record',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class UpdateRecord {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiClientService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly petId = signal('');
  readonly loading = signal(true);
  readonly vaccinationId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    vaccineName: ['', [Validators.required, Validators.maxLength(120)]],
    administeredOn: ['', Validators.required],
    nextDueOn: [''],
    provider: ['', Validators.maxLength(160)],
    notes: ['', Validators.maxLength(2000)],
  });

  constructor() {
    // The record ID in the URL also supports refreshes and direct links.
    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(
        switchMap(([params, query]) => {
          const petId = params.get('petId') ?? '';
          const vaccinationId = query.get('vaccinationId');
          this.petId.set(petId);
          this.vaccinationId.set(null);
          this.loading.set(true);
          this.error.set('');
          this.form.reset();
          if (!petId || !vaccinationId) {
            this.error.set('Choose a vaccination using Edit on the pet profile.');
            return of(null);
          }
          return this.api.getVaccinations(petId).pipe(
            map((records) => {
              const record = records.find((item) => item.id === vaccinationId && item.petId === petId);
              if (!record) this.error.set('This vaccination was not found for this pet.');
              return record ?? null;
            }),
            catchError(() => {
              this.error.set('Could not load the vaccination. Return to the pet profile and try again.');
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((record) => {
        if (record) {
          this.vaccinationId.set(record.id);
          this.form.patchValue({
            vaccineName: record.vaccineName,
            administeredOn: record.administeredOn,
            nextDueOn: record.nextDueOn ?? '',
            provider: record.provider ?? '',
            notes: record.notes ?? '',
          });
        }
        this.loading.set(false);
      });
  }

  submit(): void {
    const recordId = this.vaccinationId();
    if (this.loading() || this.saving() || !recordId) return;
    this.error.set('');
    const values = this.form.getRawValue();
    if (this.form.invalid || !values.vaccineName.trim()) {
      this.form.markAllAsTouched();
      this.error.set('Enter a vaccine name and administration date, and check the field lengths.');
      return;
    }
    if (values.nextDueOn && values.nextDueOn <= values.administeredOn) {
      this.error.set('The next due date must be after the administration date.');
      return;
    }
    const petId = this.petId();
    this.saving.set(true);
    this.form.disable({ emitEvent: false });
    this.api.updateVaccination(recordId, {
      ...values,
      vaccineName: values.vaccineName.trim(),
      nextDueOn: values.nextDueOn || null,
    }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.saving.set(false);
        this.form.enable({ emitEvent: false });
      }),
    ).subscribe({
      next: () => { void this.router.navigate(['/pets', petId]); },
      error: () => this.error.set('Could not update the vaccination. Your changes are still here. Please try again.'),
    });
  }
}
