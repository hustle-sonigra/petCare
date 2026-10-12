import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { appConfig } from './app.config';
import { AuthService } from './core/auth/auth.service';

describe('PetCare navigation and dashboard', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('creates the app', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('shows the welcome page at the root for signed-in and signed-out users', async () => {
    const token = vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue(null);
    const harness = await RouterTestingHarness.create('/');
    expect(harness.routeNativeElement?.textContent).toContain('Their health,');
    token.mockReturnValue('test-token');
    await harness.navigateByUrl('/login');
    await harness.navigateByUrl('/');
    expect(harness.routeNativeElement?.textContent).toContain('Their health,');
    expect(harness.routeNativeElement?.querySelector('.sidebar')).toBeNull();
  });

  it('shows sign in when a signed-out user requests a protected page', async () => {
    vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue(null);
    const harness = await RouterTestingHarness.create('/dashboard');
    expect(harness.routeNativeElement?.textContent).toContain('Welcome back.');
    expect(harness.routeNativeElement?.querySelector('a[href="/signup"]')?.textContent).toContain(
      'Create an account',
    );
  });

  it('keeps the legacy registration link working', async () => {
    const harness = await RouterTestingHarness.create('/signin');
    expect(harness.routeNativeElement?.textContent).toContain('A home for their health.');
  });

  it('loads real household totals inside the shared navigation', async () => {
    vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue('test-token');
    const harness = await RouterTestingHarness.create('/dashboard');
    expect(harness.routeNativeElement?.textContent).toContain('Gathering your pets');
    http
      .expectOne('/api/pets')
      .flush([{ id: 'milo', name: 'Milo', species: 'DOG', birthDate: null }]);
    http.expectOne('/api/pets/milo/vaccinations').flush([
      {
        id: 'v1',
        petId: 'milo',
        vaccineName: 'Rabies',
        administeredOn: '2026-01-10',
        nextDueOn: null,
      },
    ]);
    harness.detectChanges();
    const element = harness.routeNativeElement!;
    expect(element.querySelector('nav a[aria-current="page"]')?.textContent).toContain('Overview');
    expect(
      [...element.querySelectorAll('.summary-item strong')].map((item) => item.textContent),
    ).toEqual(['1', '0', '0', '1']);
    expect(element.querySelector('.history-list')?.textContent).toContain('Rabies');
    expect(element.querySelector('.history-list time')?.textContent).toBe('Jan 10, 2026');
  });

  it('shows a retry action instead of zero counts when records fail to load', async () => {
    vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue('test-token');
    const harness = await RouterTestingHarness.create('/dashboard');
    http.expectOne('/api/pets').flush(null, { status: 500, statusText: 'Server error' });
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Could not load your overview',
    );
    expect(harness.routeNativeElement?.querySelector('.summary-grid')).toBeNull();
    (
      harness.routeNativeElement?.querySelector('[role="alert"] button') as HTMLButtonElement
    ).click();
    http.expectOne('/api/pets').flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Add your first pet');
  });
});
