import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LocationAccessError, LocationService } from '../../core/location/location.service';
import { NearbyVets } from './nearby';

describe('NearbyVets', () => {
  let http: HttpTestingController;
  let location: LocationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NearbyVets],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    location = TestBed.inject(LocationService);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('uses dashboard coordinates after a click and displays clinic data from the API', async () => {
    location.coordinates.set({ latitude: 19.076, longitude: 72.8777 });
    const fixture = TestBed.createComponent(NearbyVets);
    fixture.detectChanges();
    http.expectNone('/api/vets/nearby');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    const request = http.expectOne((req) => req.url === '/api/vets/nearby');
    expect(request.request.params.get('lat')).toBe('19.076');
    expect(request.request.params.get('lon')).toBe('72.8777');
    request.flush([{
      id: 'node/42', name: 'City Vet', latitude: 19.08, longitude: 72.88,
      distanceKm: 0.5, address: 'Main Street', mapUrl: 'https://www.openstreetmap.org/node/42',
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vet-card')?.textContent).toContain('City Vet');
    expect(fixture.nativeElement.querySelector('.vet-card a')?.getAttribute('href'))
      .toBe('https://www.openstreetmap.org/node/42');
  });

  it('lets the user request location when none was captured on the dashboard', async () => {
    vi.spyOn(location, 'getForNearby').mockResolvedValue({ latitude: 19, longitude: 73 });
    const fixture = TestBed.createComponent(NearbyVets);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectOne((req) => req.url === '/api/vets/nearby'
      && req.params.get('lat') === '19' && req.params.get('lon') === '73').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No veterinary clinics were found nearby.');
  });

  it('shows a location message without calling the backend when permission is denied', async () => {
    vi.spyOn(location, 'getForNearby').mockRejectedValue(new LocationAccessError('denied'));
    const fixture = TestBed.createComponent(NearbyVets);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Location access was denied');
    http.expectNone('/api/vets/nearby');
  });

  it('explains an upstream outage while keeping search available for retry', async () => {
    location.coordinates.set({ latitude: 19.076, longitude: 72.8777 });
    const fixture = TestBed.createComponent(NearbyVets);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();
    http.expectOne((req) => req.url === '/api/vets/nearby')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Clinic search is temporarily unavailable');
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
  });
});
