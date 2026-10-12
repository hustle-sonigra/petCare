import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { VaccinationEntry } from './entry';

describe('Vaccination certificate uploads', () => {
  let http: HttpTestingController;
  let component: VaccinationEntry;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      imports: [VaccinationEntry],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'milo' } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.runInInjectionContext(() => new VaccinationEntry());
    component.form.patchValue({ vaccineName: 'Rabies', administeredOn: '2026-10-09' });
    component.manualEntry();
    component.verification.setValue({ vaccineName: true, administeredOn: true, nextDueOn: true, clinicName: true });
  });

  afterEach(() => http.verify());

  function selectCertificate(): File {
    const file = new File(['%PDF-1.4 test'], 'certificate.pdf', { type: 'application/pdf' });
    component.onCertificateSelected({
      target: { files: [file], value: file.name },
    } as unknown as Event);
    return file;
  }

  function saveRecord(): void {
    const request = http.expectOne('/api/vaccinations');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'vaccination-1' });
  }

  it('uploads multipart data after creating the vaccination and waits before navigating', () => {
    const file = selectCertificate();
    component.submit();
    component.submit();
    saveRecord();
    const upload = http.expectOne('/api/vaccinations/vaccination-1/certificate');
    expect(upload.request.method).toBe('PUT');
    expect(upload.request.body.get('file')).toMatchObject({
      name: file.name,
      size: file.size,
      type: file.type,
    });
    expect(upload.request.headers.has('Content-Type')).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
    expect(component.saving()).toBe(true);
    upload.flush({ vaccinationId: 'vaccination-1' });
    expect(navigate).toHaveBeenCalledWith(['/pets', 'milo']);
    expect(component.saving()).toBe(false);
  });

  it('identifies an unavailable route and retries without creating another vaccination', () => {
    selectCertificate();
    component.submit();
    saveRecord();
    http.expectOne('/api/vaccinations/vaccination-1/certificate').flush(null, {
      status: 404,
      statusText: 'Not Found',
    });
    expect(component.error()).toContain('upload service is unavailable');
    expect(component.savedRecordId()).toBe('vaccination-1');
    expect(component.form.disabled).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    component.submit();
    http.expectNone('/api/vaccinations');
    http
      .expectOne('/api/vaccinations/vaccination-1/certificate')
      .flush({ vaccinationId: 'vaccination-1' });
    expect(component.error()).toBe('');
    expect(navigate).toHaveBeenCalledOnce();
  });

  it.each([
    [413, 'Choose a file no larger than 5 MB'],
    [415, 'not a supported PDF, JPG, or PNG'],
    [500, 'Retry without creating another vaccination'],
  ])('explains upload failures with HTTP status %s', (status, message) => {
    selectCertificate();
    component.submit();
    saveRecord();
    http.expectOne('/api/vaccinations/vaccination-1/certificate').flush(null, {
      status,
      statusText: 'Upload failed',
    });
    expect(component.error()).toContain(message);
    expect(component.saving()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('saves a vaccination without an optional certificate', () => {
    component.submit();
    saveRecord();
    http.expectNone('/api/vaccinations/vaccination-1/certificate');
    expect(navigate).toHaveBeenCalledWith(['/pets', 'milo']);
  });

  it('extracts without saving, maps suggestions, and requires review before persistence', () => {
    selectCertificate();
    component.startOver();
    selectCertificate();
    component.extract();
    component.submit();
    expect(component.stage()).toBe('extracting');
    http.expectNone('/api/vaccinations');
    const extraction = http.expectOne('/api/certificates/extract');
    expect(extraction.request.body.get('file').name).toBe('certificate.pdf');
    extraction.flush({ clinicName: 'Happy Paws', vaccineName: 'DHPP', administeredOn: '2026-10-09', nextDueOn: null, warnings: { nextDueOn: 'Not found' }, evidence: {} });
    expect(component.stage()).toBe('review');
    expect(component.form.controls.provider.value).toBe('Happy Paws');
    expect(component.form.controls.vaccineName.value).toBe('DHPP');
    expect(component.warning('nextDueOn')).toBe('Not found');
    component.submit();
    http.expectNone('/api/vaccinations');
    component.verification.setValue({ vaccineName: true, administeredOn: true, nextDueOn: true, clinicName: true });
    component.submit();
    saveRecord();
    http.expectOne('/api/vaccinations/vaccination-1/certificate').flush({ vaccinationId: 'vaccination-1' });
  });

  it('clears verification when a reviewed field changes', () => {
    component.form.controls.vaccineName.setValue('DHPP');
    expect(component.verification.controls.vaccineName.value).toBe(false);
    component.submit();
    http.expectNone('/api/vaccinations');
  });

  it('returns to upload after an OCR failure and allows reviewed manual entry', () => {
    selectCertificate();
    component.extract();
    http.expectOne('/api/certificates/extract').flush(null, { status: 503, statusText: 'OCR unavailable' });
    expect(component.stage()).toBe('upload');
    expect(component.error()).toContain('enter and verify');
    component.manualEntry();
    expect(component.stage()).toBe('review');
    expect(component.verification.invalid).toBe(true);
    http.expectNone('/api/vaccinations');
  });

  it('renders an upload-first accessible review flow', () => {
    const fixture = TestBed.createComponent(VaccinationEntry);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    fixture.componentInstance.manualEntry();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('input[type=checkbox]').length).toBe(4);
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBe(true);
  });
});
