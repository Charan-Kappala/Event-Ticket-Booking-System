import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { EventService } from './services/event.service';
import { BookingService } from './services/booking.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    EventService,
    BookingService
  ]
};
