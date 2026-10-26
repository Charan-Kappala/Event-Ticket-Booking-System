import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-event-booking',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './event-booking.component.html',
  styleUrls: ['./event-booking.component.css']
})
export class EventBookingComponent implements OnInit {
  event: any;
  selectedSeats: number[] = [];
  maxSeats = 6;
  rows = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
  seatsPerRow = 10;
  seatLayout: { id: number; row: string; number: number; available: boolean; selected: boolean; }[][] = [];
  selectedTicketType: string = 'GENERAL';
  loading: boolean = false;
  error: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.initializeSeatLayout();
    this.fetchEventDetails();
  }

  initializeSeatLayout() {
    this.seatLayout = this.rows.map((row, rowIndex) => {
      return Array(this.seatsPerRow).fill(0).map((_, seatIndex) => ({
        id: rowIndex * this.seatsPerRow + seatIndex + 1,
        row: row,
        number: seatIndex + 1,
        available: true,
        selected: false
      }));
    });
  }

  async fetchEventDetails() {
    this.loading = true;
    this.error = '';
    
    const eventId = this.route.snapshot.paramMap.get('id');
    if (!eventId) {
      this.error = 'Invalid event ID.';
      this.loading = false;
      return;
    }

    try {
      const response = await fetch(`http://localhost:8080/api/events/${eventId}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch event details. Status: ${response.status}`);
      }

      this.event = await response.json();
    } catch (error) {
      console.error('Error fetching event details:', error);
      this.error = 'Failed to load event details. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  toggleSeat(seat: any) {
    if (!seat.available) return;

    if (seat.selected) {
      seat.selected = false;
      this.selectedSeats = this.selectedSeats.filter(id => id !== seat.id);
    } else if (this.selectedSeats.length < this.maxSeats) {
      seat.selected = true;
      this.selectedSeats.push(seat.id);
    } else {
      alert('Maximum 6 seats allowed per booking');
    }
  }

  selectTicketType(type: string) {
    this.selectedTicketType = type;
  }

  getTotalAmount(): number {
    if (!this.event) return 0;

    let basePrice = this.event.price || 0;
    let multiplier = this.selectedTicketType === 'VIP' ? 2 :
                     this.selectedTicketType === 'PREMIUM' ? 1.5 : 1;

    return basePrice * multiplier * this.selectedSeats.length;
  }
  
  async bookTickets() {
    if (!this.selectedSeats.length) {
      alert('Please select seats to book.');
      return;
    }

    this.loading = true;
    this.error = '';

    try {
      const response = await fetch('http://localhost:8080/api/bookings', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify({
          eventId: this.event.id,
          numberOfTickets: this.selectedSeats.length,
          seatNumbers: this.selectedSeats,
          totalPrice: this.getTotalAmount(),
          ticketType: this.selectedTicketType
        })
      });

      if (!response.ok) {
        throw new Error(`Booking failed. Status: ${response.status}`);
      }

      alert('Booking successful!');
      this.router.navigate(['/my-bookings']);
    } catch (error) {
      console.error('Error booking tickets:', error);
      this.error = 'Booking failed. Please try again.';
    } finally {
      this.loading = false;
    }
  }
}
