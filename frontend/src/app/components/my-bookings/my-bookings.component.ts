import { Component, OnDestroy, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface Booking {
  id: number;
  eventId: number;
  numberOfTickets: number;
  totalPrice: number;
  status: string;
  showTime?: string;
  holdExpiresAt?: string; // ISO instant, e.g. "2026-10-26T01:11:48.854Z"
}

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './my-bookings.component.html',
  styleUrls: ['./my-bookings.component.css']
})
export class MyBookingsComponent implements OnInit, OnDestroy {
  bookings: Booking[] = [];
  eventNames = new Map<number, string>();
  loading = false;
  error: string | null = null;
  busyBookingId: number | null = null;        // a Pay or Cancel request is in flight for this booking
  messages: Record<number, string> = {};      // per-booking feedback
  now = Date.now();                           // ticks every second for the hold countdown

  private timer?: ReturnType<typeof setInterval>;
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() {
    this.fetchUserBookings();
    if (this.isBrowser) {
      // Only in the browser: a running interval would stop server-side rendering from finishing
      this.timer = setInterval(() => (this.now = Date.now()), 1000);
    }
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  async fetchUserBookings() {
    const userId = this.isBrowser ? localStorage.getItem('userId') : null;
    if (!userId) {
      return;
    }
    this.loading = true;
    this.error = null;
    try {
      const [bookingsResponse, eventsResponse] = await Promise.all([
        this.api.fetch(`/api/bookings/users/${userId}`),
        this.api.fetch('/api/events')
      ]);
      if (!bookingsResponse.ok) {
        throw new Error(`HTTP ${bookingsResponse.status}`);
      }
      this.bookings = await bookingsResponse.json();
      if (eventsResponse.ok) {
        const events: Array<{ id: number; name: string }> = await eventsResponse.json();
        this.eventNames = new Map(events.map(e => [e.id, e.name]));
      }
    } catch (error) {
      console.error('Error fetching bookings:', error);
      this.error = 'Could not load your bookings. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  /** Seconds left on a PENDING booking's hold (0 once it has run out). */
  secondsLeft(booking: Booking): number {
    if (!booking.holdExpiresAt) return 0;
    return Math.max(0, Math.floor((Date.parse(booking.holdExpiresAt) - this.now) / 1000));
  }

  formatCountdown(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
  }

  isHoldActive(booking: Booking): boolean {
    return booking.status === 'PENDING' && this.secondsLeft(booking) > 0;
  }

  isHoldExpired(booking: Booking): boolean {
    return booking.status === 'EXPIRED' || (booking.status === 'PENDING' && this.secondsLeft(booking) === 0);
  }

  canCancel(booking: Booking): boolean {
    return booking.status === 'CONFIRMED' || this.isHoldActive(booking);
  }

  pickSeatsAgain(booking: Booking) {
    this.router.navigate(['/book-tickets', booking.eventId]);
  }

  async cancelBooking(booking: Booking) {
    this.busyBookingId = booking.id;
    delete this.messages[booking.id];
    try {
      const response = await this.api.fetch(`/api/bookings/${booking.id}`, { method: 'DELETE' });
      if (response.ok) {
        await this.fetchUserBookings();
      } else if (response.status !== 401) {
        this.messages[booking.id] = await this.api.errorMessage(response, 'Could not cancel this booking.');
      }
    } catch {
      this.messages[booking.id] = 'Could not reach the server. Please try again.';
    } finally {
      this.busyBookingId = null;
    }
  }

  async confirmPayment(booking: Booking) {
    // One key per booking's payment; a retry after a failure reuses it, so it can't pay twice.
    // The payment reference is derived from the key, so the retried request is identical.
    const action = `pay:${booking.id}`;
    const key = this.api.idempotencyKeyFor(action);
    this.busyBookingId = booking.id;
    delete this.messages[booking.id];
    try {
      const response = await this.api.fetch(
        `/api/bookings/${booking.id}/confirm-payment?paymentId=PAY-${key}`, { method: 'PUT' }, key);
      if (response.status === 401) {
        return;
      }
      if (response.status >= 500) {
        this.messages[booking.id] = 'Payment did not go through. Press Confirm Payment again to retry safely.';
        return;
      }
      this.api.finishAction(action);
      if (response.status === 409) {
        const message = await this.api.errorMessage(response, '');
        this.messages[booking.id] = message.includes('expired')
          ? 'Your hold expired before payment. Please pick seats again.'
          : message || 'This booking can no longer be paid for.';
      } else if (!response.ok) {
        this.messages[booking.id] = await this.api.errorMessage(response, 'Payment failed.');
      }
      await this.fetchUserBookings();
    } catch {
      this.messages[booking.id] = 'Could not reach the server. Press Confirm Payment again to retry safely.';
    } finally {
      this.busyBookingId = null;
    }
  }
}
