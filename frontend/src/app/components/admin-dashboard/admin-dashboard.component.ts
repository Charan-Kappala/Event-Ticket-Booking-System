import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../auth/auth.service';

interface Event {
  id: number;
  name: string;
  category: string;
  description: string;
  venue: string;
  eventDateTime: string;
  bookingCount: number;
  totalSeats: number;
  availableSeats: number;
  price: number;
  bookings: any[];
  analytics?: {
    totalRevenue: number;
    occupancyRate: number;
    salesByShowTime: { [key: string]: number };
  };
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  events: Event[] = [];
  selectedEvent: Event | null = null;
  showAnalytics = false;
  ticketCount = 1;
  ticketOptions = [1, 2, 3, 4, 5, 6]; // Options for ticket selection

  loading = false;
  error: string | null = null;

  constructor(private router: Router, private api: ApiService, private auth: AuthService) {}

  getTotalPlatformSeats(): number {
    return this.events.reduce((sum, e) => sum + (e.totalSeats || 0), 0);
  }

  getTotalPlatformBookings(): number {
    return this.events.reduce((sum, e) => sum + (e.bookings?.length || 0), 0);
  }

  ngOnInit() {
    this.fetchEvents();
  }

  async fetchEvents() {
    this.loading = true;
    this.error = null;
    try {
      const response = await this.api.fetch('/api/events');

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      {
        const data = await response.json();
        this.events = await Promise.all(data.map(async (event: any) => {
          const [bookings, analytics] = await Promise.all([
            this.fetchBookings(event.id),
            this.fetchEventAnalytics(event.id)
          ]);

          return {
            ...event,
            bookings,
            analytics,
            bookingCount: bookings.length
            // availableSeats comes from the server, which already excludes cancelled and expired bookings
          };
        }));
      }
    } catch (error) {
      console.error('Error fetching events:', error);
      this.error = 'Could not load events. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  async fetchBookings(eventId: number) {
    try {
      const response = await this.api.fetch(`/api/bookings/event/${eventId}`);
      return response.ok ? await response.json() : [];
    } catch (error) {
      console.error('Error fetching bookings:', error);
      return [];
    }
  }

  async fetchEventAnalytics(eventId: number) {
    try {
      const response = await this.api.fetch(`/api/events/${eventId}/analytics`);
      return response.ok ? await response.json() : null;
    } catch (error) {
      console.error('Error fetching analytics:', error);
      return null;
    }
  }

  async viewAnalytics(event: Event) {
    try {
      const response = await this.api.fetch(`/api/events/${event.id}/analytics`);

      if (response.ok) {
        const analyticsData = await response.json();
        this.selectedEvent = {
          ...event,
          analytics: analyticsData
        };
        this.showAnalytics = true;
      } else {
        console.error('Failed to fetch analytics');
        alert('Failed to load analytics data');
      }
    } catch (error) {
      console.error('Error fetching analytics:', error);
      alert('Error loading analytics data');
    }
  }

  closeAnalytics() {
    this.showAnalytics = false;
    this.selectedEvent = null;
  }

  logout() {
    this.auth.logout();
  }

  bookTickets() {
    if (!this.selectedEvent) {
      console.error('No event selected');
      return;
    }

    if (this.ticketCount > 6) {
      alert('You can book a maximum of 6 tickets');
      return;
    }

    // Logic to book tickets
    console.log(`Booking ${this.ticketCount} tickets for event: ${this.selectedEvent.name}`);
    // Implement booking logic here, e.g., call a booking API
  }
}