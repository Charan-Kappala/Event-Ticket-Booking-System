import { Component, OnInit, ChangeDetectorRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SeatingChartComponent } from '../seating-chart/seating-chart.component';
import { Event } from '../../models/event.model';
import { ApiService } from '../../services/api.service';

type TimeSlots = {
  [key: string]: string[];
  morning: string[];
  matinee: string[];
  evening: string[];
  night: string[];
};

@Component({
  selector: 'app-book-tickets',
  standalone: true,
  imports: [CommonModule, FormsModule, SeatingChartComponent],
  templateUrl: './book-tickets.component.html',
  styleUrls: ['./book-tickets.component.css']
})
export class BookTicketsComponent implements OnInit {
  show: Event | null = null;
  loading = false;
  error: string | null = null;
  selectedSeats: Array<{row: string; number: number}> = [];
  selectedTime: string = '';
  selectedTicketType: string = 'GENERAL';
  timeTypes = ['morning', 'matinee', 'evening', 'night'];
  showTimesByType: TimeSlots = {
    morning: ['10:00 AM', '11:30 AM'],
    matinee: ['2:00 PM', '3:30 PM'],
    evening: ['6:00 PM', '7:30 PM'],
    night: ['9:00 PM', '10:30 PM']
  };
  showSeatSelection = false;
  ticketCount: number = 1;
  @ViewChild(SeatingChartComponent) seatingChart?: SeatingChartComponent;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private api: ApiService
  ) {}

  ngOnInit() {
    const eventId = this.route.snapshot.paramMap.get('id');
    if (eventId) {
      this.fetchEventDetails(eventId);
    }
  }

  getSelectedSeatsDisplay(): string {
    if (!this.selectedSeats || this.selectedSeats.length === 0) {
      return 'No seats selected';
    }
    return this.selectedSeats
      .map(seat => `${seat.row}${seat.number}`)
      .join(', ');
  }

  async fetchEventDetails(id: string) {
    this.loading = true;
    this.error = null;
    try {
      const response = await this.api.fetch(`/api/events/${id}`);
      if (!response.ok) {
        throw new Error('Failed to fetch event details');
      }
      this.show = await response.json();
      console.log('Fetched event details:', this.show);
      
      if (this.show) {
        this.showSeatSelection = this.show.category === 'MOVIE' || this.show.category === 'THEATRE';
        if (this.show.category === 'THEATRE') {
          this.selectedTime = this.show.eventDateTime;
        }
      } else {
        this.showSeatSelection = false;
      }
    } catch (error) {
      console.error('Error fetching event details:', error);
      this.error = 'Failed to load event details. Please try again.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  selectTicketType(type: string) {
    this.selectedTicketType = type;
    this.selectedSeats = []; // Reset selected seats when changing ticket type
  }

  getTicketPrice(): number {
    if (!this.show) return 0;
    
    switch (this.selectedTicketType) {
      case 'VIP':
        return this.show.vipPrice || this.show.price * 2;
      case 'PREMIUM':
        return this.show.premiumPrice || this.show.price * 1.5;
      case 'GENERAL':
        return this.show.generalPrice || this.show.price;
      default:
        return this.show.price;
    }
  }

  getTotalPrice(): number {
    if (!this.show) return 0;
    
    const price = this.getTicketPrice();
    if (this.show.category === 'MOVIE' || this.show.category === 'THEATRE') {
      return price * (this.selectedSeats.length || 1);
    } else {
      return price * this.ticketCount;
    }
  }

  onSeatsSelected(seats: Array<{row: string; number: number}>) {
    this.selectedSeats = seats;
    this.ticketCount = seats.length;
    this.error = null;
    this.cdr.detectChanges();
  }

  async bookTickets() {
    if (!this.show) {
      this.error = 'Event details not found';
      return;
    }

    if ((this.show.category === 'MOVIE' || this.show.category === 'THEATRE') && !this.selectedTime) {
      this.error = 'Please select a show time';
      return;
    }

    if (this.show.category === 'CONCERT' || this.show.category === 'SPORTS') {
      if (this.ticketCount <= 0) {
        this.error = 'Please select at least one ticket';
        return;
      }
    } else {
      if (this.selectedSeats.length === 0) {
        this.error = 'Please select seats to continue';
        return;
      }
    }

    const userId = localStorage.getItem('userId');
    if (!userId) {
      this.router.navigate(['/login']);
      return;
    }

    this.loading = true;
    this.error = null;

    try {
      const bookingData = {
        eventId: this.show.id,
        userId: userId,
        seatNumbers: this.show.category === 'MOVIE' || this.show.category === 'THEATRE' 
          ? this.selectedSeats.map(seat => `${seat.row}${seat.number}`)
          : Array(this.ticketCount).fill('UNRESERVED'),
        showTime: this.selectedTime || this.show.eventDateTime,
        ticketType: this.selectedTicketType,
        numberOfTickets: this.show.category === 'MOVIE' || this.show.category === 'THEATRE'
          ? this.selectedSeats.length
          : this.ticketCount,
        totalPrice: this.getTotalPrice(),
        paymentStatus: 'COMPLETED'
      };

      // Same booking request => same key, so a retry after a network error can't book twice
      const action = `book:${JSON.stringify(bookingData)}`;
      const response = await this.api.fetch(`/api/bookings/users/${userId}`, {
        method: 'POST',
        body: JSON.stringify(bookingData)
      }, this.api.idempotencyKeyFor(action));

      if (response.status === 401) {
        return; // ApiService already sent the user to the login page
      }
      if (response.status >= 500) {
        // Keep the idempotency key: pressing Confirm again retries the same request safely
        this.error = 'Something went wrong on our side. Please press Confirm Booking again.';
        return;
      }

      this.api.finishAction(action);
      if (response.status === 409) {
        this.error = this.showSeatSelection
          ? 'That seat was just taken, please pick another.'
          : 'Sorry, there are not enough tickets left.';
        this.selectedSeats = [];
        this.seatingChart?.reload(); // show the seat as taken now
        return;
      }
      if (!response.ok) {
        this.error = await this.api.errorMessage(response, 'Could not create the booking. Please try again.');
        return;
      }

      alert('Seats held for 5 minutes. Confirm payment in My Bookings to keep them.');
      this.router.navigate(['/my-bookings']);
    } catch (error: any) {
      // Network failure: we don't know if the booking was made. Retrying reuses the key, so it's safe.
      console.error('Error creating booking:', error);
      this.error = 'Could not reach the server. Press Confirm Booking again to retry safely.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  formatTime(time: string): string {
    return time;
  }

  canShowBookingSummary(): boolean {
    if (!this.show) return false;

    if (this.show.category === 'MOVIE' || this.show.category === 'THEATRE') {
      return this.selectedTime !== '' && this.selectedSeats.length > 0;
    } else {
      return this.ticketCount > 0;
    }
  }

  selectShowTime(time: string) {
    this.selectedTime = time;
    this.selectedSeats = []; // Reset seats when changing time
    this.error = null;
    this.cdr.detectChanges();
  }
}
