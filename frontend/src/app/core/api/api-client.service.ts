import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DashboardSummary {
  petCount: number;
  upcomingCount: number;
  overdueCount: number;
  recentVaccinations: VaccinationSummary[];
}

export interface PetSummary {
  id: string;
  name: string;
  species: string;
  birthDate: string | null;
  breed?: string | null;
}

export interface AddPetRequest {
  name: string;
  species: string;
  breed: string;
  birthDate: string | null;
}

export interface VaccinationRecord {
  id: string;
  petId: string;
  vaccineName: string;
  administeredOn: string;
  nextDueOn: string | null;
  provider: string | null;
  notes: string | null;
}

export interface AddVaccinationRequest {
  petId: string;
  vaccineName: string;
  administeredOn: string;
  nextDueOn: string | null;
  provider: string;
  notes: string;
}

export type UpdateVaccinationRequest = Omit<AddVaccinationRequest, 'petId'>;

export interface VaccinationSummary {
  id: string;
  petId: string;
  vaccineName: string;
  administeredOn: string;
  nextDueOn: string | null;
}
export interface VaccinationCertificate {
  vaccinationId: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}

export type ExtractionField = 'clinicName' | 'vaccineName' | 'administeredOn' | 'nextDueOn';
export interface VaccinationExtraction {
  clinicName: string | null;
  vaccineName: string | null;
  administeredOn: string | null;
  nextDueOn: string | null;
  warnings: Partial<Record<ExtractionField, string>>;
  evidence: Partial<Record<ExtractionField, string>>;
}

@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api';

  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.apiUrl}/dashboard`);
  }

  getPets(): Observable<PetSummary[]> {
    return this.http.get<PetSummary[]>(`${this.apiUrl}/pets`);
  }

  getVaccinations(petId: string): Observable<VaccinationRecord[]> {
    return this.http.get<VaccinationRecord[]>(`${this.apiUrl}/pets/${petId}/vaccinations`);
  }

  getPet(petId: string): Observable<PetSummary> {
    return this.http.get<PetSummary>(`${this.apiUrl}/pets/${petId}`);
  }

  addPet(request: AddPetRequest): Observable<PetSummary> {
    return this.http.post<PetSummary>(`${this.apiUrl}/pets`, request);
  }

  addVaccination(request: AddVaccinationRequest): Observable<VaccinationRecord> {
    return this.http.post<VaccinationRecord>(`${this.apiUrl}/vaccinations`, request);
  }

  updateVaccination(recordId: string, request: UpdateVaccinationRequest): Observable<VaccinationRecord> {
    return this.http.put<VaccinationRecord>(`${this.apiUrl}/vaccinations/${recordId}`, request);
  }
  extractCertificate(file: File): Observable<VaccinationExtraction> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<VaccinationExtraction>(`${this.apiUrl}/certificates/extract`, body);
  }
  uploadVaccinationCertificate(
  vaccinationId: string,
  file: File,
): Observable<VaccinationCertificate> {
  const body = new FormData();
  body.append('file', file, file.name);

  return this.http.put<VaccinationCertificate>(
    `${this.apiUrl}/vaccinations/${vaccinationId}/certificate`,
    body,
  );
}

getVaccinationCertificateMetadata(
  vaccinationId: string,
): Observable<VaccinationCertificate> {
  return this.http.get<VaccinationCertificate>(
    `${this.apiUrl}/vaccinations/${vaccinationId}/certificate/metadata`,
  );
}

downloadVaccinationCertificate(vaccinationId: string): Observable<Blob> {
  return this.http.get(
    `${this.apiUrl}/vaccinations/${vaccinationId}/certificate`,
    { responseType: 'blob' },
  );
}

deleteVaccine(recordId: string):Observable<void>{
return this.http.delete<void>(
    `${this.apiUrl}/vaccinations/${recordId}`,
  );
}
}
