import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CertificatePage } from './certificate';
import { AuthService } from '../../../core/auth/auth.service';
import { authInterceptor } from '../../../core/auth/auth.interceptor';

describe('Certificate preview', () => {
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
        static override createObjectURL = vi
          .fn()
          .mockReturnValue('blob:http://localhost/certificate');
        static override revokeObjectURL = vi.fn();
      },
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'pets/:petId/vaccinations/:vaccinationId/certificate',
            component: CertificatePage,
          },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue('test-token');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function open(): Promise<RouterTestingHarness> {
    const harness = await RouterTestingHarness.create('/pets/milo/vaccinations/v1/certificate');
    http.expectOne('/api/pets/milo/vaccinations').flush([{ id: 'v1', vaccineName: 'Rabies' }]);
    return harness;
  }

  it('previews an authenticated PDF and releases its temporary URL on navigation', async () => {
    const harness = await open();
    http.expectOne('/api/vaccinations/v1/certificate/metadata').flush(metadata);
    const download = http.expectOne('/api/vaccinations/v1/certificate');
    expect(download.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(download.request.responseType).toBe('blob');
    const blob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    download.flush(blob);
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('iframe')?.getAttribute('src')).toBe(
      'blob:http://localhost/certificate',
    );
    expect(harness.routeNativeElement?.textContent).toContain('milo.pdf');
    expect(harness.routeNativeElement?.querySelector('img')).toBeNull();
    harness.fixture.destroy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/certificate');
  });

  it('previews an image instead of embedding it as a PDF', async () => {
    const harness = await open();
    http
      .expectOne('/api/vaccinations/v1/certificate/metadata')
      .flush({ ...metadata, originalFilename: 'milo.png', contentType: 'image/png' });
    http
      .expectOne('/api/vaccinations/v1/certificate')
      .flush(new Blob(['png'], { type: 'image/png' }));
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('img')?.getAttribute('src')).toBe(
      'blob:http://localhost/certificate',
    );
    expect(harness.routeNativeElement?.querySelector('iframe')).toBeNull();
  });

  it('shows a retry action for a missing certificate', async () => {
    const harness = await open();
    http.expectOne('/api/vaccinations/v1/certificate/metadata').flush(metadata);
    http
      .expectOne('/api/vaccinations/v1/certificate')
      .flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Could not open this certificate',
    );
    expect(harness.routeNativeElement?.querySelector('.certificate-preview')).toBeNull();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('checks the vaccination belongs to the pet in the URL before fetching its file', async () => {
    const harness = await RouterTestingHarness.create('/pets/other/vaccinations/v1/certificate');
    http.expectOne('/api/pets/other/vaccinations').flush([]);
    http.expectNone('/api/vaccinations/v1/certificate');
    http.expectNone('/api/vaccinations/v1/certificate/metadata');
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')).not.toBeNull();
  });
});
