import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, switchMap, throwError } from 'rxjs';
import { ApiClientService, VaccinationCertificate } from '../../../core/api/api-client.service';
import { downloadFile } from '../../../shared/utils/download';

@Component({
  selector: 'app-certificate-page',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './certificate.html',
  styleUrl: './certificate.scss',
})
export class CertificatePage {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);
  readonly petId = this.route.snapshot.paramMap.get('petId') ?? '';
  readonly vaccinationId = this.route.snapshot.paramMap.get('vaccinationId') ?? '';
  readonly metadata = signal<VaccinationCertificate | null>(null);
  readonly vaccineName = signal('');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly blobUrl = signal('');
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  private blob: Blob | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.releasePreview());
    this.load();
  }

  load(): void {
    this.releasePreview();
    this.loading.set(true);
    this.error.set('');
    this.metadata.set(null);
    this.blob = null;
    this.api
      .getVaccinations(this.petId)
      .pipe(
        switchMap((records) => {
          const record = records.find((item) => item.id === this.vaccinationId);
          if (!record) return throwError(() => new Error('Certificate record not found'));
          this.vaccineName.set(record.vaccineName);
          return forkJoin({
            metadata: this.api.getVaccinationCertificateMetadata(this.vaccinationId),
            blob: this.api.downloadVaccinationCertificate(this.vaccinationId),
          });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ metadata, blob }) => {
          if (!['application/pdf', 'image/jpeg', 'image/png'].includes(blob.type)) {
            this.error.set(
              'This certificate format cannot be previewed. Download it from the pet profile.',
            );
            this.loading.set(false);
            return;
          }
          this.metadata.set(metadata);
          this.blob = blob;
          const url = URL.createObjectURL(blob);
          this.blobUrl.set(url);
          // Only a locally created Blob URL with a supported content type is trusted.
          if (blob.type === 'application/pdf')
            this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
          this.loading.set(false);
        },
        error: () => {
          this.error.set(
            'Could not open this certificate. It may be unavailable or you may need to sign in again.',
          );
          this.loading.set(false);
        },
      });
  }

  download(): void {
    const metadata = this.metadata();
    if (this.blob && metadata) downloadFile(this.blob, metadata.originalFilename);
  }

  private releasePreview(): void {
    if (this.blobUrl()) URL.revokeObjectURL(this.blobUrl());
    this.blobUrl.set('');
    this.pdfUrl.set(null);
  }
}
