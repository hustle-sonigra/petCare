import { Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiClientService } from '../../../core/api/api-client.service';

@Component({
  selector: 'app-add-pet',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class AddPet {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiClientService);
  private readonly router = inject(Router);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    species: ['DOG', Validators.required],
    breed: [''],
    birthDate: [''],
  });
  submit(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.api
      .addPet({ ...value, birthDate: value.birthDate || null })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (pet) => this.router.navigate(['/pets', pet.id]),
        error: () => this.error.set('Could not save your pet. Please try again.'),
      });
  }
}
