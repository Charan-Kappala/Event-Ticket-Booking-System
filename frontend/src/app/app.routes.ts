import { Routes } from '@angular/router';
import { BookTicketsComponent } from './components/book-tickets/book-tickets.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { ShowsComponent } from './components/shows/shows.component';
import { OrganizerDashboardComponent } from './components/organizer-dashboard/organizer-dashboard.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { MyBookingsComponent } from './components/my-bookings/my-bookings.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'shows', component: ShowsComponent },
  { path: 'organizer-dashboard', component: OrganizerDashboardComponent },
  { path: 'admin-dashboard', component: AdminDashboardComponent },
  { path: 'book-tickets/:id', component: BookTicketsComponent },
  { path: 'my-bookings', component: MyBookingsComponent },
  {
    path: 'seat-selection/:id',
    loadComponent: () => import('./components/seating-chart/seating-chart.component')
      .then(m => m.SeatingChartComponent)
  }
];