import { Component, Input, Output, EventEmitter, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { ActivatedRoute, Router } from '@angular/router';

// Shape returned by GET /api/events/{id}/seats
interface SeatResponse {
  id: number;
  seatIdentifier: string; // e.g. "A1"
  available: boolean;
  price: number;
}

interface Seat {
  id: number;
  row: string;
  number: number;
  isAvailable: boolean;
  isSelected: boolean;
  price: number;
}

@Component({
  selector: 'app-seating-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="seating-chart-wrapper">
      <!-- Curved Cinema Screen -->
      <div class="screen-stage">
        <div class="screen-curve"></div>
        <div class="screen-light-glow"></div>
        <div class="screen-text">
          <i class="fa-solid fa-film"></i>
          <span>CINEMA SCREEN THIS WAY</span>
        </div>
      </div>

      <!-- Ticket Limit & Selection Pill -->
      <div class="seat-status-bar">
        <div class="status-pill">
          <i class="fa-solid fa-circle-info"></i>
          <span>Maximum {{ maxTickets }} tickets per booking</span>
        </div>
        <div class="status-pill highlight" [class.full]="getRemainingTickets() === 0">
          <i class="fa-solid fa-ticket"></i>
          <span>{{ selectedSeats.length }} selected ({{ getRemainingTickets() }} remaining)</span>
        </div>
      </div>

      <!-- Loading State -->
      <div *ngIf="loadingSeats" class="chart-loading">
        <div class="spinner"></div>
        <p>Loading real-time seating availability...</p>
      </div>

      <!-- Error State -->
      <div *ngIf="seatError" class="seat-error-banner">
        <i class="fa-solid fa-circle-exclamation"></i>
        <span>{{ seatError }}</span>
      </div>

      <!-- Interactive Seat Matrix -->
      <div class="seat-map-scroll" *ngIf="!loadingSeats">
        <div class="seats-container">
          <div *ngFor="let row of seatMap" class="seat-row">
            <span class="row-label">{{ row[0].row }}</span>
            <div class="row-seats">
              <button 
                *ngFor="let seat of row" 
                type="button"
                class="seat-btn" 
                [class.available]="seat.isAvailable"
                [class.selected]="seat.isSelected"
                [class.unavailable]="!seat.isAvailable"
                [disabled]="!seat.isAvailable"
                (click)="selectSeat(seat)"
                [title]="seat.isAvailable ? 'Seat ' + seat.row + seat.number + ' - ' + (seat.price | currency) : 'Seat ' + seat.row + seat.number + ' is Booked'">
                <span class="seat-headrest"></span>
                <span class="seat-num">{{ seat.number }}</span>
              </button>
            </div>
            <span class="row-label">{{ row[0].row }}</span>
          </div>
        </div>
      </div>

      <!-- Legend -->
      <div class="seat-legend">
        <div class="legend-item">
          <div class="legend-swatch available"></div>
          <span>Available</span>
        </div>
        <div class="legend-item">
          <div class="legend-swatch selected"></div>
          <span>Selected</span>
        </div>
        <div class="legend-item">
          <div class="legend-swatch unavailable"></div>
          <span>Booked / Held</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .seating-chart-wrapper {
      padding: 24px 16px;
      background: rgba(15, 23, 42, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 20px;
      overflow: hidden;
    }

    /* Screen Stage */
    .screen-stage {
      position: relative;
      text-align: center;
      margin: 12px auto 36px;
      max-width: 520px;
    }

    .screen-curve {
      height: 6px;
      background: linear-gradient(90deg, transparent 0%, #06b6d4 25%, #6366f1 50%, #06b6d4 75%, transparent 100%);
      border-radius: 50% 50% 0 0;
      box-shadow: 0 0 20px rgba(6, 182, 212, 0.6), 0 0 35px rgba(99, 102, 241, 0.4);
    }

    .screen-light-glow {
      height: 35px;
      background: linear-gradient(180deg, rgba(6, 182, 212, 0.15) 0%, transparent 100%);
      filter: blur(10px);
      margin-top: 2px;
    }

    .screen-text {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.15em;
      color: #94a3b8;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-top: -12px;
      text-transform: uppercase;
    }

    .screen-text i {
      color: #06b6d4;
    }

    /* Status Bar */
    .seat-status-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-bottom: 24px;
      flex-wrap: wrap;
    }

    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 9999px;
      font-size: 0.78rem;
      font-weight: 600;
      color: #94a3b8;
    }

    .status-pill.highlight {
      background: rgba(99, 102, 241, 0.12);
      border-color: rgba(99, 102, 241, 0.3);
      color: #c7d2fe;
    }

    .status-pill.highlight.full {
      background: rgba(245, 158, 11, 0.12);
      border-color: rgba(245, 158, 11, 0.3);
      color: #fde68a;
    }

    /* Seat Matrix Scroll */
    .seat-map-scroll {
      overflow-x: auto;
      padding: 12px 6px;
      display: flex;
      justify-content: center;
    }

    .seats-container {
      display: inline-flex;
      flex-direction: column;
      gap: 10px;
      align-items: center;
      min-width: min-content;
    }

    .seat-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .row-label {
      width: 22px;
      text-align: center;
      font-weight: 800;
      font-size: 0.85rem;
      color: #64748b;
      font-family: monospace;
    }

    .row-seats {
      display: flex;
      gap: 8px;
    }

    /* Seat Buttons */
    .seat-btn {
      position: relative;
      width: 36px;
      height: 36px;
      border-radius: 8px 8px 6px 6px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-family: inherit;
      border: 1px solid transparent;
      outline: none;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      padding: 0;
    }

    .seat-headrest {
      width: 18px;
      height: 3px;
      border-radius: 2px;
      background: rgba(255, 255, 255, 0.2);
      margin-bottom: 2px;
    }

    .seat-num {
      font-size: 0.72rem;
      font-weight: 700;
      line-height: 1;
    }

    /* Available Seat */
    .seat-btn.available {
      background: rgba(16, 185, 129, 0.08);
      border-color: rgba(16, 185, 129, 0.35);
      color: #6ee7b7;
    }

    .seat-btn.available .seat-headrest {
      background: rgba(16, 185, 129, 0.4);
    }

    .seat-btn.available:hover {
      background: rgba(16, 185, 129, 0.25);
      border-color: #10b981;
      color: #ffffff;
      transform: translateY(-3px) scale(1.08);
      box-shadow: 0 4px 14px rgba(16, 185, 129, 0.4);
    }

    /* Selected Seat */
    .seat-btn.selected {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      border-color: #34d399;
      color: #ffffff;
      transform: scale(1.05);
      box-shadow: 0 0 16px rgba(16, 185, 129, 0.5);
    }

    .seat-btn.selected .seat-headrest {
      background: rgba(255, 255, 255, 0.6);
    }

    /* Booked / Unavailable Seat */
    .seat-btn.unavailable {
      background: rgba(255, 255, 255, 0.03);
      border-color: rgba(255, 255, 255, 0.06);
      color: #475569;
      cursor: not-allowed;
      opacity: 0.45;
    }

    .seat-btn.unavailable .seat-headrest {
      background: rgba(255, 255, 255, 0.08);
    }

    /* Legend */
    .seat-legend {
      display: flex;
      justify-content: center;
      gap: 24px;
      margin-top: 28px;
      padding-top: 20px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      flex-wrap: wrap;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #94a3b8;
    }

    .legend-swatch {
      width: 22px;
      height: 22px;
      border-radius: 6px;
    }

    .legend-swatch.available {
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid #10b981;
    }

    .legend-swatch.selected {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      border: 1px solid #34d399;
      box-shadow: 0 0 10px rgba(16, 185, 129, 0.4);
    }

    .legend-swatch.unavailable {
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.1);
      opacity: 0.5;
    }

    .chart-loading {
      padding: 30px;
      text-align: center;
      color: #94a3b8;
    }

    .chart-loading p {
      margin-top: 12px;
    }

    .seat-error-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      background: rgba(244, 63, 94, 0.15);
      border: 1px solid rgba(244, 63, 94, 0.3);
      border-radius: 10px;
      color: #fda4af;
      font-size: 0.85rem;
      margin: 12px auto;
      max-width: 420px;
    }
  `]
})
export class SeatingChartComponent implements OnInit {
  @Input() eventId!: number;
  @Input() eventName: string = '';
  @Input() venue: string = '';
  @Input() price: number = 0;
  @Output() seatSelected = new EventEmitter<Seat[]>();
  @Output() proceedToBooking = new EventEmitter<void>();

  seatMap: Seat[][] = [];
  selectedSeats: Seat[] = [];
  maxTickets = 6;
  loadingSeats = false;
  seatError: string | null = null;

  constructor(private router: Router, private route: ActivatedRoute, private cdr: ChangeDetectorRef,
              private api: ApiService) {}

  /**
   * Reloads seat availability from the server and clears the current selection.
   * Doesn't emit seatSelected: the parent called this and already cleared its own selection
   * (emitting would also wipe the parent's "seat was just taken" message).
   */
  reload() {
    this.selectedSeats = [];
    this.loadSeats();
  }

  ngOnInit() {
    // Embedded in book-tickets the event ID is an input; on the /seat-selection/:id route it's in the URL
    this.eventId = this.eventId ?? Number(this.route.snapshot.paramMap.get('id'));
    this.loadSeats();
  }

  // Seat availability comes from the backend, so seats held or booked by anyone show as Booked
  private async loadSeats() {
    this.loadingSeats = true;
    this.seatError = null;
    try {
      const response = await this.api.fetch(`/api/events/${this.eventId}/seats`);
      if (!response.ok) {
        throw new Error('Failed to load seats');
      }
      const seats: SeatResponse[] = await response.json();
      this.seatMap = this.buildSeatMap(seats);
    } catch (error) {
      console.error('Error loading seats:', error);
      this.seatError = 'Could not load seats. Please try again.';
    } finally {
      this.loadingSeats = false;
      this.cdr.detectChanges();
    }
  }

  // Groups seats like "A1".."G10" into rows A..G, each sorted by seat number
  private buildSeatMap(seats: SeatResponse[]): Seat[][] {
    const rows = new Map<string, Seat[]>();
    for (const seat of seats) {
      const row = seat.seatIdentifier.charAt(0);
      const number = parseInt(seat.seatIdentifier.substring(1), 10);
      if (!rows.has(row)) {
        rows.set(row, []);
      }
      rows.get(row)!.push({
        id: seat.id,
        row,
        number,
        isAvailable: seat.available,
        isSelected: false,
        price: seat.price || this.price || 0
      });
    }
    return [...rows.keys()].sort().map(row => rows.get(row)!.sort((a, b) => a.number - b.number));
  }

  selectSeat(seat: Seat) {
    if (!seat.isAvailable) {
      alert('This seat is not available');
      return;
    }
    
    if (seat.isSelected) {
      seat.isSelected = false;
      this.selectedSeats = this.selectedSeats.filter(s => s.id !== seat.id);
    } else if (this.selectedSeats.length < this.maxTickets) {
      seat.isSelected = true;
      this.selectedSeats.push(seat);
    } else {
      alert(`Maximum ${this.maxTickets} tickets allowed per booking`);
      return;
    }
    
    console.log('Selected seats:', this.selectedSeats.map(s => `${s.row}${s.number}`).join(', '));
    this.seatSelected.emit(this.selectedSeats);
  }

  getRemainingTickets(): number {
    return this.maxTickets - this.selectedSeats.length;
  }

  getSelectedSeatsLabel(): string {
    return this.selectedSeats
      .map(seat => `${seat.row}${seat.number}`)
      .join(', ');
  }

  getTotalAmount(): number {
    return this.selectedSeats.length * (this.price || 0);
  }

  proceedToPayment() {
    if (this.selectedSeats.length === 0) {
      alert('Please select at least one seat');
      return;
    }
    this.seatSelected.emit(this.selectedSeats);
    this.proceedToBooking.emit();
  }
}