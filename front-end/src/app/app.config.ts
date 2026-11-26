import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { provideHotToastConfig } from '@ngxpert/hot-toast';

export const appConfig: ApplicationConfig = {
  providers: [
    // HU-11 · Each new page starts at the top and "Atrás" goes back to where you were. Catalog sort, category and
    // page changes (HU-10) also go to the top: they're picked at the top of the list or, for the pager, mean a new list.
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([authInterceptor])), 
    provideHotToastConfig({ style: {marginTop: '70px'}, stacking: 'depth', duration: 3000}),
  ]
};
