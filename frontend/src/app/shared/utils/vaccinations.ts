import { VaccinationSummary } from '../../core/api/api-client.service';

export function calendarDate(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function upcomingLimit(date = new Date()): string {
  const limit = new Date(date);
  limit.setDate(limit.getDate() + 30);
  return calendarDate(limit);
}

// A newer administration of the same named vaccine replaces its previous due date.
// Keep every administration in history, including records with no due date.
export function latestVaccinations<T extends VaccinationSummary>(records: T[]): T[] {
  const latest = new Map<string, T>();
  for (const record of [...records].sort((a, b) =>
    b.administeredOn.localeCompare(a.administeredOn),
  )) {
    const key = `${record.petId}:${record.vaccineName.trim().toLowerCase()}`;
    if (!latest.has(key)) latest.set(key, record);
  }
  return [...latest.values()];
}
