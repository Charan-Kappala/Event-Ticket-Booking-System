import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

interface Event {
  id: number;
  name: string;
  category: string;
  description: string;
  venue: string;
  eventDateTime: string;
  price: number;
  totalSeats: number;
  availableSeats: number;
}

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="events-container">
      <div class="category-filters">
        <button (click)="sortEvents('all')" class="filter-btn">All Events</button>
        <button (click)="sortEvents('MOVIE')" class="filter-btn">Movies</button>
        <button (click)="sortEvents('CONCERTS')" class="filter-btn">Concerts</button>
        <button (click)="sortEvents('THEATRE')" class="filter-btn">Theatre</button>
        <button (click)="sortEvents('SPORTS')" class="filter-btn">Sports</button>
      </div>

      <div class="events-grid">
        <div *ngFor="let event of filteredEvents" class="event-card">
          <div class="event-info">
            <h3>{{ event.name }}</h3>
            <p class="venue">{{ event.venue }}</p>
            <p class="datetime">{{ event.eventDateTime | date:'medium' }}</p>
            <p class="category">Category: {{ event.category }}</p>
            <p class="price">Price: {{ event.price | currency }}</p>
            <p class="seats">Available Seats: {{ event.availableSeats }}/{{ event.totalSeats }}</p>
            <button 
              class="book-btn" 
              (click)="bookEvent(event.id)"
              [disabled]="event.availableSeats === 0">
              {{ event.availableSeats === 0 ? 'Sold Out' : 'Book Now' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .events-container {
      padding: 20px;
    }

    .category-filters {
      margin-bottom: 20px;
      display: flex;
      gap: 10px;
      justify-content: center;
    }

    .filter-btn {
      padding: 8px 16px;
      border: none;
      border-radius: 4px;
      background-color: #f0f0f0;
      cursor: pointer;
      transition: background-color 0.3s;
    }

    .filter-btn:hover {
      background-color: #e0e0e0;
    }

    .events-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 20px;
    }

    .event-card {
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      overflow: hidden;
      transition: transform 0.3s;
    }

    .event-card:hover {
      transform: translateY(-5px);
    }

    .event-info {
      padding: 15px;
    }

    .event-info h3 {
      margin: 0 0 10px 0;
      color: #333;
    }

    .venue, .datetime, .category, .price, .seats {
      margin: 5px 0;
      color: #666;
    }

    .book-btn {
      width: 100%;
      padding: 10px;
      margin-top: 10px;
      border: none;
      border-radius: 4px;
      background-color: #4CAF50;
      color: white;
      cursor: pointer;
      transition: background-color 0.3s;
    }

    .book-btn:hover:not([disabled]) {
      background-color: #45a049;
    }

    .book-btn[disabled] {
      background-color: #cccccc;
      cursor: not-allowed;
    }
  `]
})
export class EventsComponent implements OnInit {
  events: Event[] = [];
  filteredEvents: Event[] = [];

  constructor(private router: Router) {}

  ngOnInit() {
    this.loadEvents();
  }

  async loadEvents() {
    try {
      const response = await fetch('http://localhost:8080/api/events', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      if (response.ok) {
        this.events = await response.json();
        this.filteredEvents = [...this.events];
      }
    } catch (error) {
      console.error('Error loading events:', error);
    }
  }

  sortEvents(category: string) {
    console.log('Selected category:', category);
    console.log('Available events:', this.events);
    
    if (category === 'all') {
      this.filteredEvents = [...this.events];
    } else {
      this.filteredEvents = this.events.filter(event => {
        console.log('Event category from DB:', event.category);
        console.log('Category we are filtering for:', category);
        return event.category && event.category.toUpperCase() === category.toUpperCase();
      });
    }
    console.log('Filtered results:', this.filteredEvents);
}

  bookEvent(eventId: number) {
    const event = this.events.find(e => e.id === eventId);
    if (event && event.category === 'MOVIE') {
      this.router.navigate(['/book-tickets', eventId]);
    } else {
      this.router.navigate(['/seat-selection', eventId]);
    }
  }
}