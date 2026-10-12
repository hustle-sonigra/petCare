import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiClientService, PetSummary } from '../../../core/api/api-client.service';

@Component({
  selector: 'app-pets-page',
  imports: [RouterLink],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class PetsPage {
  private readonly api = inject(ApiClientService);
  readonly pets = signal<PetSummary[]>([]);
  readonly error = signal('');
  readonly loading = signal(true);
  constructor() {
    this.loadPets();
  }
  loadPets(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getPets().subscribe({
      next: (pets) => {
        this.pets.set(pets);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load your pets. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
