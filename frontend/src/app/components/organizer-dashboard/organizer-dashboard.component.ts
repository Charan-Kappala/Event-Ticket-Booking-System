import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Event } from '../../models/event.model';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../auth/auth.service';

interface EventAnalytics {
  totalBookings: number;
  totalRevenue: number;
  averageRating: number;
}

interface EventWithAnalytics extends Event {
  analytics?: EventAnalytics;
}

@Component({
  selector: 'app-organizer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './organizer-dashboard.component.html',
  styleUrls: ['./organizer-dashboard.component.css']
})
export class OrganizerDashboardComponent implements OnInit {
  events: Event[] = [];
  selectedEvent: Event | null = null;
  showEventForm = false;
  currentEvent: Event = {
    name: '',
    description: '',
    category: 'MOVIE',
    venue: '',
    eventDateTime: '',
    price: 0,
    vipPrice: 0,
    premiumPrice: 0,
    generalPrice: 0,
    totalSeats: 0,
    availableSeats: 0
  } as Event;

  isEditing = false;

  loading = false;
  error: string | null = null;

  constructor(private router: Router, private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.fetchEvents();
  }

  async fetchEvents() {
    this.loading = true;
    this.error = null;
    try {
      const response = await this.api.fetch('/api/events');
      if (!response.ok) throw new Error('Failed to fetch events');
      const data = await response.json();
      // Organizers manage only their own events (the backend enforces this too)
      const myId = this.auth.getCurrentUser()?.id;
      this.events = data
        .filter((event: any) => event.organizerId === myId)
        .map((event: any) => ({ ...event, id: Number(event.id) }));
    } catch (error) {
      console.error('Error fetching events:', error);
      this.error = 'Could not load your events. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  editEvent(event: Event) {
    this.isEditing = true;
    this.currentEvent = { ...event };
    this.showEventForm = true;
  }

  async deleteEvent(eventId: number) {
    if (!confirm('Are you sure you want to delete this event?')) return;

    try {
      const response = await this.api.fetch(`/api/events/${eventId}`, {
        method: 'DELETE'
      });

      if (response.ok) {
        this.events = this.events.filter(event => event.id !== eventId);
      } else {
        alert(await this.api.errorMessage(response, 'Failed to delete event'));
      }
    } catch (error) {
      console.error('Error deleting event:', error);
      alert('Error deleting event');
    }
  }

  async saveEvent() {
    if (!this.isEditing) {
      const { id, ...eventWithoutId } = this.currentEvent;
      this.currentEvent = eventWithoutId as Event;
    }

    const path = this.isEditing ? `/api/events/${this.currentEvent.id}` : '/api/events';

    try {
      const response = await this.api.fetch(path, {
        method: this.isEditing ? 'PUT' : 'POST',
        body: JSON.stringify(this.currentEvent)
      });

      if (!response.ok) {
        alert('Failed to save event: ' + await this.api.errorMessage(response, 'please check the form'));
      } else {
        await this.fetchEvents();
        this.showEventForm = false;
        this.resetForm();
      }
    } catch (error) {
      console.error('Error saving event:', error);
      alert('Error saving event');
    }
  }

  async fetchEventAnalytics(eventId: number) {
    try {
      const response = await this.api.fetch(`/api/events/${eventId}/analytics`);
      if (!response.ok) throw new Error('Failed to fetch analytics');
      const event = this.events.find(e => e.id === eventId);
      if (event) {
        const analytics = await response.json();
        const totalTicketsSold = event.totalSeats - event.availableSeats;
        this.selectedEvent = {
          ...event,
          analytics: {
            totalBookings: totalTicketsSold,
            totalRevenue: analytics.totalRevenue,
            averageRating: analytics.averageRating
          }
        };
      }
    } catch (error) {
      console.error('Error fetching analytics:', error);
    }
  }
  resetForm() {
    this.currentEvent = {
        name: '',
        description: '',
        category: 'MOVIE',
        venue: '',
        eventDateTime: '',
        price: 0,
        vipPrice: 0,
        premiumPrice: 0,
        generalPrice: 0,
        totalSeats: 0,
        availableSeats: 0
    } as Event;
    this.isEditing = false;
  }

  cancelEdit() {
    this.showEventForm = false;
    this.resetForm();
  }

  logout() {
    this.auth.logout();
  }
}