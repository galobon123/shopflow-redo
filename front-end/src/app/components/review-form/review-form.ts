import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CircleAlert, LucideAngularModule, Star } from 'lucide-angular';
import { ReviewService } from '../../core/services/review.service';
import { Toaster } from '../../core/services/toaster.service';
import { applyServerErrors, errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfField, SfInput } from '../../ui';

export const MAX_COMMENT = 1000;

/**
 * HU-30 · "Calificar": stars (a native radio group, so arrows move between them) and an optional comment.
 * Only shown to someone who received the product and hasn't reviewed it; the server checks it again.
 */
@Component({
  selector: 'app-review-form',
  imports: [ReactiveFormsModule, LucideAngularModule, SfAlert, SfButton, SfField, SfInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="sf-review-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h3 class="text-title">Calificá este producto</h3>
      @if (formError(); as message) {
        <sf-alert>{{ message }}</sf-alert>
      }

      <fieldset class="sf-rate" [attr.aria-describedby]="ratingError() ? 'rating-error' : null">
        <legend class="sf-field__label">Tu calificación</legend>
        <div class="sf-rate__stars">
          @for (n of stars; track n) {
            <label class="sf-rate__star">
              <input class="sr-only" type="radio" formControlName="rating" [value]="n" />
              <lucide-icon [img]="starIcon" [size]="28" aria-hidden="true" />
              <span class="sr-only">{{ n }} {{ n === 1 ? 'estrella' : 'estrellas' }}</span>
            </label>
          }
        </div>
        @if (ratingError(); as message) {
          <span class="sf-field__error" id="rating-error">
            <lucide-icon [img]="alertIcon" [size]="16" />{{ message }}
          </span>
        }
      </fieldset>

      <sf-field class="max-w-none" label="Comentario (opcional)" [hint]="'Hasta ' + maxComment + ' caracteres.'"
        [errors]="{ maxlength: 'El comentario puede tener hasta ' + maxComment + ' caracteres.' }">
        <textarea sfInput class="sf-input--area" formControlName="comment" rows="4"
          placeholder="¿Qué te pareció? Calce, tela, lo que te sirva contarle a otro."></textarea>
      </sf-field>

      <button sfButton="primary" type="submit" class="justify-self-start" [loading]="saving()">Publicar reseña</button>
    </form>
  `,
})
export class ReviewForm {
  private readonly fb = inject(FormBuilder);
  private readonly reviewService = inject(ReviewService);
  private readonly toaster = inject(Toaster);

  readonly productId = input.required<number>();
  /** The review was published. */
  readonly saved = output<void>();

  protected readonly form = this.fb.group({
    rating: this.fb.control<number | null>(null, Validators.required),
    comment: this.fb.nonNullable.control('', Validators.maxLength(MAX_COMMENT)),
  });
  protected readonly saving = signal(false);
  /** 403/409 or no connection: a message about the whole form. */
  protected readonly formError = signal<string | null>(null);

  protected readonly stars = [1, 2, 3, 4, 5];
  protected readonly maxComment = MAX_COMMENT;
  protected readonly starIcon = Star;
  protected readonly alertIcon = CircleAlert;

  /** Like sf-field: shown once touched; a text error is the server's message. */
  protected ratingError(): string | null {
    const control = this.form.controls.rating;
    if (!control.invalid || !control.touched) return null;
    const server = control.errors?.['server'];
    return typeof server === 'string' ? server : 'Elegí de 1 a 5 estrellas.';
  }

  submit() {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { rating, comment } = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.reviewService.create(this.productId(), { rating: rating!, comment: comment.trim() || null }).subscribe({
      next: () => {
        this.saving.set(false);
        this.toaster.success('¡Gracias! Tu reseña ya está publicada.');
        this.saved.emit();
      },
      error: (err) => {
        this.saving.set(false);
        if (!applyServerErrors(this.form, err)) this.formError.set(errorMessage(err));
      },
    });
  }
}
