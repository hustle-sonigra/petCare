import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { PetDetails } from '../../pets/details/details';
import { UpdateRecord } from './js';

describe('Vaccination edit navigation and prefill', () => {
  let http: HttpTestingController;
  const record = {
    id: 'v1', petId: 'milo', vaccineName: 'Rabies', administeredOn: '2026-10-09',
    nextDueOn: null, provider: 'Vet clinic', notes: 'First dose',
  };

  beforeEach(() => {
    // Reuse the real route paths without requiring login or the workspace shell.
    const children = routes.find((route) => route.children)?.children ?? [];
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(children)],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('navigates from Edit to the registered update page with the selected record ID', async () => {
    const harness = await RouterTestingHarness.create();
    const details = await harness.navigateByUrl('/pets/milo', PetDetails);
    http.expectOne('/api/pets/milo').flush({ id: 'milo', name: 'Milo', species: 'DOG' });
    http.expectOne('/api/pets/milo/vaccinations').flush([record]);
    http.expectOne('/api/vaccinations/v1/certificate/metadata').flush(null, {
      status: 404, statusText: 'Not Found',
    });
    harness.detectChanges();
    const edit = Array.from(harness.routeNativeElement!.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'EDIT')!;
    edit.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/pets/milo/vaccinations/update?vaccinationId=v1');
    http.expectOne('/api/pets/milo/vaccinations').flush([record]);
    harness.detectChanges();
    expect((harness.routeNativeElement!.querySelector('#vaccine') as HTMLInputElement).value)
      .toBe('Rabies');
  });

  it('prefills all fields on a direct link without navigation state', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(
      '/pets/milo/vaccinations/update?vaccinationId=v1', UpdateRecord,
    );
    http.expectOne('/api/pets/milo/vaccinations').flush([record]);
    expect(component.form.getRawValue()).toEqual({
      vaccineName: 'Rabies', administeredOn: '2026-10-09', nextDueOn: '',
      provider: 'Vet clinic', notes: 'First dose',
    });
  });

  it('shows an error when the record ID is missing', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/pets/milo/vaccinations/update', UpdateRecord);
    expect(component.error()).toContain('Choose a vaccination');
    expect(component.loading()).toBe(false);
    http.expectNone('/api/pets/milo/vaccinations');
  });

  it('does not prefill a record that does not belong to this pet', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(
      '/pets/milo/vaccinations/update?vaccinationId=v1', UpdateRecord,
    );
    http.expectOne('/api/pets/milo/vaccinations').flush([{ ...record, petId: 'another-pet' }]);
    expect(component.error()).toContain('not found');
    expect(component.form.controls.vaccineName.value).toBe('');
  });

  async function openEditor() {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(
      '/pets/milo/vaccinations/update?vaccinationId=v1', UpdateRecord,
    );
    http.expectOne('/api/pets/milo/vaccinations').flush([record]);
    harness.detectChanges();
    return { harness, component };
  }

  it('updates the selected record once and navigates only after the server succeeds', async () => {
    const { harness, component } = await openEditor();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.form.patchValue({ vaccineName: 'Updated rabies', notes: 'Booster' });
    (harness.routeNativeElement!.querySelector('button[type=submit]') as HTMLButtonElement).click();
    component.submit();
    const request = http.expectOne('/api/vaccinations/v1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      vaccineName: 'Updated rabies', administeredOn: '2026-10-09', nextDueOn: null,
      provider: 'Vet clinic', notes: 'Booster',
    });
    expect(component.saving()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    request.flush({ ...record, vaccineName: 'Updated rabies', notes: 'Booster' });
    expect(navigate).toHaveBeenCalledWith(['/pets', 'milo']);
    expect(component.saving()).toBe(false);
  });

  it('keeps the form and edits available for retry when saving fails', async () => {
    const { harness, component } = await openEditor();
    component.form.controls.notes.setValue('Keep this edit');
    component.submit();
    http.expectOne('/api/vaccinations/v1').flush(null, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.notes.value).toBe('Keep this edit');
    expect(harness.routeNativeElement!.querySelector('form')).not.toBeNull();
    expect(component.error()).toContain('Could not update');
    component.submit();
    http.expectOne('/api/vaccinations/v1').flush(null, { status: 500, statusText: 'Server Error' });
  });

  it('rejects blank names and due dates on or before the administration date', async () => {
    const { component } = await openEditor();
    component.form.controls.vaccineName.setValue('   ');
    component.submit();
    expect(component.error()).toContain('Enter a vaccine name');
    component.form.controls.vaccineName.setValue('Rabies');
    component.form.controls.nextDueOn.setValue('2026-10-09');
    component.submit();
    expect(component.error()).toContain('must be after');
    http.expectNone('/api/vaccinations/v1');
  });
});
