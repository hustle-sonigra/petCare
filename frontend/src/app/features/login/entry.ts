import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Router } from '@angular/router';
import { inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-entry',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './index.html',
  styleUrl: './style.css',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly error = signal('');
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set('');
    this.auth.clear();

    const { email, password } = this.form.getRawValue();

    this.auth
      .login(email, password)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.router.navigate(['/dashboard']);
        },

        error: (response) => {
          this.error.set(
            response.status === 401
              ? 'Invalid email or password.'
              : 'Could not sign in. Please try again.',
          );
        },
      });
  }
}
