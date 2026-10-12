import { calendarDate, latestVaccinations, upcomingLimit } from './vaccinations';
import { VaccinationSummary } from '../../core/api/api-client.service';

describe('Vaccination dates and history', () => {
  it('uses calendar days across month and year boundaries', () => {
    const date = new Date(2026, 11, 20, 23, 45);
    expect(calendarDate(date)).toBe('2026-12-20');
    expect(upcomingLimit(date)).toBe('2027-01-19');
    expect(calendarDate(date)).toBe('2026-12-20');
  });

  it('keeps only the latest due record for each pet and named vaccine without removing history', () => {
    const record = (
      id: string,
      petId: string,
      vaccineName: string,
      administeredOn: string,
      nextDueOn: string | null,
    ): VaccinationSummary => ({ id, petId, vaccineName, administeredOn, nextDueOn });
    const history = [
      record('old', 'milo', 'Rabies', '2025-01-01', '2026-01-01'),
      record('new', 'milo', ' rabies ', '2026-02-01', null),
      record('other-pet', 'luna', 'Rabies', '2025-06-01', '2026-06-01'),
      record('other-vaccine', 'milo', 'Other vaccine', '2025-05-01', '2026-05-01'),
    ];
    expect(latestVaccinations(history).map((item) => item.id)).toEqual([
      'new',
      'other-pet',
      'other-vaccine',
    ]);
    expect(history).toHaveLength(4);
    expect(latestVaccinations(history)[0].nextDueOn).toBeNull();
  });
});
