import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, output, viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { AuthStore } from '../../auth-store';
import { UserService } from '../../core/services/user.service';
import { Toaster } from '../../core/services/toaster.service';

/** The bits of Google Identity Services this button uses. */
interface GoogleIdentity {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (response: { credential: string }) => void; ux_mode: 'popup' }): void;
      renderButton(parent: HTMLElement, options: Record<string, string | number>): void;
    };
  };
}

const GIS_URL = 'https://accounts.google.com/gsi/client';
/** Google draws its button between these widths, in px. */
const MIN_WIDTH = 200;
const MAX_WIDTH = 400;

/** Google's button has a fixed width: as wide as the room it gets, within the limits Google accepts. */
export function buttonWidth(available: number): number {
  return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.floor(available)));
}
let gisScript: Promise<GoogleIdentity> | undefined;

/** Loads Google's script once per page, and only when the button is going to be shown. */
function loadGoogleIdentity(): Promise<GoogleIdentity> {
  gisScript ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GIS_URL;
    script.async = true;
    script.onload = () => resolve((window as unknown as { google: GoogleIdentity }).google);
    script.onerror = () => {
      gisScript = undefined;
      reject(new Error('Google Identity Services did not load'));
    };
    document.head.appendChild(script);
  });
  return gisScript;
}

/**
 * HU-32 · "Continuar con Google". Google's own button (their brand rules), rendered by their script.
 * Hidden when the server has no GOOGLE_CLIENT_ID, or if the script can't load (blocked, offline).
 */
@Component({
  selector: 'app-google-sign-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (clientId()) {
      <div class="flex items-center gap-3 my-6 text-body-s text-ink-muted" aria-hidden="true">
        <span class="h-px flex-1 bg-line"></span>o<span class="h-px flex-1 bg-line"></span>
      </div>
      <div #button class="flex justify-center min-h-10 w-full min-w-0"></div>
    }
  `,
})
export class GoogleSignIn {
  private readonly authStore = inject(AuthStore);
  private readonly toaster = inject(Toaster);
  private readonly destroyRef = inject(DestroyRef);

  /** The session started; the page decides where to go. */
  readonly signedIn = output<void>();

  private readonly userService = inject(UserService);
  private readonly config = rxResource({ stream: () => this.userService.config() });
  protected readonly clientId = computed(() => (this.config.hasValue() ? this.config.value().googleClientId : null));
  private readonly button = viewChild<ElementRef<HTMLElement>>('button');

  constructor() {
    effect(() => {
      const clientId = this.clientId();
      const host = this.button()?.nativeElement;
      if (clientId && host) this.render(clientId, host);
    });
  }

  private async render(clientId: string, host: HTMLElement) {
    let google: GoogleIdentity;
    try {
      google = await loadGoogleIdentity();
    } catch {
      return; // Password sign-in still works; nothing to show.
    }
    google.accounts.id.initialize({
      client_id: clientId,
      ux_mode: 'popup',
      callback: ({ credential }) => this.signIn(credential),
    });
    // Drawn again whenever the room changes (a narrow phone, rotating it), so it never spills out of the card.
    let drawnWidth = 0;
    const draw = (available: number) => {
      const width = buttonWidth(available);
      if (available <= 0 || width === drawnWidth) return;
      drawnWidth = width;
      host.replaceChildren();
      google.accounts.id.renderButton(host, {
        type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular',
        locale: 'es-419', width,
      });
    };
    const resizes = new ResizeObserver(([entry]) => draw(entry.contentRect.width));
    resizes.observe(host);
    this.destroyRef.onDestroy(() => resizes.disconnect());
  }

  private signIn(credential: string) {
    const subscription = this.authStore.signInWithGoogle(credential).subscribe({
      next: () => this.signedIn.emit(),
      error: (err) => this.toaster.error(err),
    });
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
  }
}
