import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PetDetails } from './details';

describe('Certificates on pet profiles', () => {
  let http: HttpTestingController;
  const metadata = {
    vaccinationId: 'v1',
    originalFilename: 'milo.pdf',
    contentType: 'application/pdf',
    sizeBytes: 12,
  };

  beforeEach(() => {
    const OriginalURL = URL;
    vi.stubGlobal(
      'URL',
      class extends OriginalURL {
        static override createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/download');
        static override revokeObjectURL = vi.fn();
      },
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'pets/:petId', component: PetDetails }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function open(): Promise<RouterTestingHarness> {
    const harness = await RouterTestingHarness.create('/pets/milo');
    http
      .expectOne('/api/pets/milo')
      .flush({ id: 'milo', name: 'Milo', species: 'DOG', birthDate: null });
    http.expectOne('/api/pets/milo/vaccinations').flush([
      {
        id: 'v1',
        petId: 'milo',
        vaccineName: 'Rabies',
        administeredOn: '2026-10-09',
        nextDueOn: null,
      },
    ]);
    return harness;
  }

  it('shows view and download actions and downloads with the original filename', async () => {
    const harness = await open();
    http.expectOne('/api/vaccinations/v1/certificate/metadata').flush(metadata);
    harness.detectChanges();
    const element = harness.routeNativeElement!;
    expect(element.querySelector('.certificate-actions a')?.getAttribute('href')).toBe(
      '/pets/milo/vaccinations/v1/certificate',
    );
    expect(element.textContent).toContain('milo.pdf');
    vi.useFakeTimers();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    (element.querySelector('.certificate-actions button') as HTMLButtonElement).click();
    const request = http.expectOne('/api/vaccinations/v1/certificate');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
    expect(click).toHaveBeenCalledOnce();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('milo.pdf');
    vi.advanceTimersByTime(1000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/download');
  });

  it('keeps records visible when no certificate was uploaded', async () => {
    const harness = await open();
    http
      .expectOne('/api/vaccinations/v1/certificate/metadata')
      .flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('No certificate uploaded.');
    expect(harness.routeNativeElement?.textContent).toContain('Rabies');
    expect(harness.routeNativeElement?.querySelector('.certificate-actions')).toBeNull();
  });

  it('offers retry for a metadata failure instead of reporting no certificate', async () => {
    const harness = await open();
    http
      .expectOne('/api/vaccinations/v1/certificate/metadata')
      .flush(null, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).not.toContain('No certificate uploaded.');
    (
      harness.routeNativeElement?.querySelector('.certificate-error button') as HTMLButtonElement
    ).click();
    http.expectOne('/api/vaccinations/v1/certificate/metadata').flush(metadata);
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('.certificate-actions')).not.toBeNull();
  });
});
