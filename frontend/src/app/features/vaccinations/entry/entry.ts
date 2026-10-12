import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, map, of, switchMap, tap } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiClientService, VaccinationExtraction, ExtractionField } from '../../../core/api/api-client.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-vaccination-entry',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class VaccinationEntry {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiClientService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly stage = signal<'upload' | 'extracting' | 'review'>('upload');
  readonly extraction = signal<VaccinationExtraction | null>(null);
  readonly petId = this.route.snapshot.paramMap.get('petId') ?? '';
  readonly error = signal('');
  readonly saving = signal(false);
  certificateFile: File | null = null;
  readonly certificateError = signal('');
  readonly savedRecordId = signal<string | null>(null);
  readonly verification = this.fb.nonNullable.group({
    vaccineName: [false, Validators.requiredTrue],
    administeredOn: [false, Validators.requiredTrue],
    nextDueOn: [false, Validators.requiredTrue],
    clinicName: [false, Validators.requiredTrue],
  });
  readonly form = this.fb.nonNullable.group({
    vaccineName: ['', [Validators.required, Validators.maxLength(120)]],
    administeredOn: ['', Validators.required],
    nextDueOn: [''],
    provider: [''],
    notes: [''],
  });

  constructor() {
    // Editing a reviewed value requires confirmation again before saving.
    for (const [control, field] of [
      ['vaccineName', 'vaccineName'], ['administeredOn', 'administeredOn'],
      ['nextDueOn', 'nextDueOn'], ['provider', 'clinicName'],
    ] as const) {
      this.form.controls[control].valueChanges
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.verification.controls[field].setValue(false));
    }
  }

  warning(field: ExtractionField): string {
    return this.extraction()?.warnings[field] ?? 'Check this value against the certificate. Optional fields may be left empty.';
  }

  manualEntry(): void {
    if (this.stage() === 'extracting' || this.saving()) return;
    this.stage.set('review');
    this.certificateError.set('');
    this.error.set('');
    this.verification.reset();
    this.focusReview();
  }

  extract(): void {
    if (!this.certificateFile || this.stage() === 'extracting' || this.saving()) return;
    this.stage.set('extracting');
    this.error.set('');
    this.api.extractCertificate(this.certificateFile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.extraction.set(result);
          this.form.patchValue({
            vaccineName: result.vaccineName ?? '', provider: result.clinicName ?? '',
            administeredOn: result.administeredOn ?? '', nextDueOn: result.nextDueOn ?? '',
          });
          this.verification.reset();
          this.stage.set('review');
          this.focusReview();
        },
        error: (response: HttpErrorResponse) => {
          this.stage.set('upload');
          this.error.set(response.status === 401 ? 'Please sign in again before extracting a certificate.'
            : response.status === 503 ? 'Extraction is unavailable or busy. Retry, or enter and verify the details manually.'
            : response.status === 415 ? 'Choose a PDF, JPG, or PNG certificate.'
            : response.status === 413 ? 'Choose a file no larger than 5 MB.'
            : 'Could not read this certificate. Retry with another file, or enter the details manually.');
        },
      });
  }

  startOver(): void {
    if (this.saving() || this.savedRecordId()) return;
    this.certificateFile = null;
    this.extraction.set(null);
    this.form.reset();
    this.verification.reset();
    this.error.set('');
    this.certificateError.set('');
    this.stage.set('upload');
  }

  private focusReview(): void {
    setTimeout(() => document.getElementById('review-heading')?.focus());
  }
  onCertificateSelected(event: Event): void {
    if (this.saving() || this.stage() === 'extracting') return;

    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.certificateFile = null;
    this.certificateError.set('');

    if (!file) return;

    if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
      this.certificateError.set('Choose a PDF, JPG, or PNG file.');
      input.value = '';
      return;
    }

    if (file.size === 0 || file.size > 5 * 1024 * 1024) {
      this.certificateError.set('Choose a non-empty file no larger than 5 MB.');
      input.value = '';
      return;
    }

    this.certificateFile = file;
  }

  clearCertificate(input: HTMLInputElement): void {
    if (this.saving()) return;
    input.value = '';
    this.certificateFile = null;
    this.certificateError.set('');
  }

  submit(): void {
    if (this.saving() || this.certificateError() || this.stage() !== 'review') return;
    if (this.verification.invalid) {
      this.verification.markAllAsTouched();
      this.error.set('Verify each field before saving, including optional fields left empty.');
      return;
    }
    if (this.form.invalid || !this.petId) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    if (values.nextDueOn && values.nextDueOn <= values.administeredOn) {
      this.error.set('The next due date must be after the administration date. Check your certificate.');
      this.verification.controls.nextDueOn.setValue(false);
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const certificate = this.certificateFile;
    const savedId = this.savedRecordId();
    const recordId$ = savedId
      ? of(savedId)
      : this.api
          .addVaccination({
            petId: this.petId,
            ...this.form.getRawValue(),
            nextDueOn: this.form.controls.nextDueOn.value || null,
          })
          .pipe(
            map((record) => record.id),
            tap((id) => {
              this.savedRecordId.set(id);
              this.form.disable({ emitEvent: false });
            }),
          );

    recordId$
      .pipe(
        switchMap((id) =>
          certificate ? this.api.uploadVaccinationCertificate(id, certificate) : of(null),
        ),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => this.router.navigate(['/pets', this.petId]),
        error: (response: HttpErrorResponse) => {
          this.error.set(
            this.savedRecordId()
              ? this.certificateUploadError(response)
              : 'Could not save the vaccination. Please try again.',
          );
        },
      });
  }

  private certificateUploadError(response: HttpErrorResponse): string {
    const prefix = 'Vaccination saved. ';
    switch (response.status) {
      case 0:
        return prefix + 'Could not connect to the upload service. Check your connection and retry.';
      case 404:
      case 405:
      case 503:
        return prefix + 'The certificate upload service is unavailable. Please try again shortly.';
      case 413:
        return prefix + 'The certificate is too large. Choose a file no larger than 5 MB.';
      case 415:
        return (
          prefix + 'The file is not a supported PDF, JPG, or PNG certificate. Choose another file.'
        );
      case 400:
        return (
          prefix + 'The certificate was rejected. Choose a non-empty PDF, JPG, or PNG up to 5 MB.'
        );
      default:
        return (
          prefix + 'Could not upload the certificate. Retry without creating another vaccination.'
        );
    }
  }
}
