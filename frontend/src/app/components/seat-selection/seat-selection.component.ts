import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-seat-selection',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="seat-selection-container">
      <div class="screen" *ngIf="eventCategory !== 'THEATRE'">Screen</div>
      <div class="seats-container">
        <div *ngFor="let row of seats; let i = index" class="seat-row">
          <span class="row-label">{{String.fromCharCode(65 + i)}}</span>
          <div *ngFor="let seat of row" 
               class="seat" 
               [class.occupied]="seat.occupied"
               [class.selected]="seat.selected"
               (click)="toggleSeat(seat)">
            {{seat.number}}
          </div>
        </div>
      </div>
      <div class="legend">
        <div class="legend-item">
          <div class="seat-sample available"></div>
          <span>Available</span>
        </div>
        <div class="legend-item">
          <div class="seat-sample occupied"></div>
          <span>Occupied</span>
        </div>
        <div class="legend-item">
          <div class="seat-sample selected"></div>
          <span>Selected</span>
        </div>
      </div>
      <button class="btn btn-primary book-btn" [disabled]="!hasSelectedSeats()">
        Book Tickets
      </button>
    </div>
  `,
  styles: [`
    .seat-selection-container {
      padding: 20px;
      max-width: 1000px;
      margin: 0 auto;
    }
    .screen {
      background: #e0e0e0;
      height: 50px;
      margin: 20px 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 5px;
      transform: perspective(100px) rotateX(-5deg);
    }
    .seats-container {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin: 30px 0;
    }
    .seat-row {
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .row-label {
      width: 30px;
      text-align: center;
    }
    .seat {
      width: 35px;
      height: 35px;
      border: 1px solid #ccc;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border-radius: 5px;
      background: white;
    }
    .seat.occupied {
      background: #e0e0e0;
      cursor: not-allowed;
    }
    .seat.selected {
      background: #e91e63;
      color: white;
    }
    .legend {
      display: flex;
      justify-content: center;
      gap: 20px;
      margin: 20px 0;
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 5px;
    }
    .seat-sample {
      width: 20px;
      height: 20px;
      border: 1px solid #ccc;
      border-radius: 3px;
    }
    .seat-sample.available {
      background: white;
    }
    .seat-sample.occupied {
      background: #e0e0e0;
    }
    .seat-sample.selected {
      background: #e91e63;
    }
    .book-btn {
      width: 100%;
      max-width: 300px;
      margin: 20px auto;
      display: block;
    }
  `]
})
export class SeatSelectionComponent {
  String = String; // Make String available in template

  seats = Array(8).fill(null).map((_, rowIndex) =>
    Array(12).fill(null).map((_, seatIndex) => ({
      number: seatIndex + 1,
      occupied: Math.random() > 0.7,
      selected: false
    }))
  );
  route: any;

  toggleSeat(seat: any) {
    if (!seat.occupied) {
      seat.selected = !seat.selected;
    }
  }

  hasSelectedSeats() {
    return this.seats.some(row => row.some(seat => seat.selected));
  }

  // Add to the component class:
  eventCategory: string = '';

  ngOnInit() {
    // Get the event ID from the route
    const eventId = this.route.snapshot.paramMap.get('id');
    if (eventId) {
      this.fetchEventDetails(eventId);
    }
  }

  async fetchEventDetails(eventId: string) {
    try {
      const response = await fetch(`http://localhost:8080/api/events/${eventId}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      if (response.ok) {
        const event = await response.json();
        this.eventCategory = event.category;
      }
    } catch (error) {
      console.error('Error fetching event details:', error);
    }
  }
}