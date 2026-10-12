import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import {
  ApiClientService,
  PetSummary,
  VaccinationSummary,
} from '../../core/api/api-client.service';
import { calendarDate, latestVaccinations, upcomingLimit } from '../../shared/utils/vaccinations';

interface HouseholdRecord extends VaccinationSummary {
  petName: string;
}

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly api = inject(ApiClientService);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly pets = signal<PetSummary[]>([]);
  readonly records = signal<HouseholdRecord[]>([]);
  readonly today = calendarDate();
  private readonly limit = upcomingLimit();
  readonly currentRecords = computed(() => latestVaccinations(this.records()));
  readonly upcoming = computed(() =>
    this.currentRecords()
      .filter(
        (record) =>
          record.nextDueOn && record.nextDueOn >= this.today && record.nextDueOn <= this.limit,
      )
      .sort((a, b) => a.nextDueOn!.localeCompare(b.nextDueOn!)),
  );
  readonly overdue = computed(() =>
    this.currentRecords()
      .filter((record) => record.nextDueOn && record.nextDueOn < this.today)
      .sort((a, b) => a.nextDueOn!.localeCompare(b.nextDueOn!)),
  );
  readonly recent = computed(() =>
    [...this.records()]
      .sort((a, b) => b.administeredOn.localeCompare(a.administeredOn))
      .slice(0, 5),
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .getPets()
      .pipe(
        switchMap((pets) => {
          const requests = pets.map((pet) =>
            this.api
              .getVaccinations(pet.id)
              .pipe(map((records) => records.map((record) => ({ ...record, petName: pet.name })))),
          );
          return (requests.length ? forkJoin(requests) : of([] as HouseholdRecord[][])).pipe(
            map((groups) => ({ pets, records: groups.flat() })),
          );
        }),
      )
      .subscribe({
        next: ({ pets, records }) => {
          this.pets.set(pets);
          this.records.set(records);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not load your overview. Please try again.');
          this.loading.set(false);
        },
      });
  }
}
