import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { provideHotToastConfig } from '@ngxpert/hot-toast';
import { Toaster } from './core/services/toaster.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])), 
    provideHotToastConfig({ style: {marginTop: '70px'}, stacking: 'depth', duration: 3000}),
    // Starts the toaster right away so it sees the first click (it ignores repeats while a toast is up).
    provideAppInitializer(() => void inject(Toaster)),
  ]
};
