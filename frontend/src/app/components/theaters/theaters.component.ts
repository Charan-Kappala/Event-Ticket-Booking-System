import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-theaters',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="theaters-container">
      <h2>Available Theaters</h2>
      <div class="theaters-list">
        <div *ngFor="let theater of theaters" class="theater-card" (click)="selectTheater(theater)">
          <div class="theater-info">
            <h3>{{theater.name}}</h3>
            <p>{{theater.location}}</p>
          </div>
          <div class="showtimes">
            <button *ngFor="let time of theater.showTimes" class="time-btn">
              {{time}}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .theaters-container {
      padding: 20px;
      max-width: 1200px;
      margin: 0 auto;
    }
    .theaters-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .theater-card {
      background: white;
      padding: 1rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      cursor: pointer;
    }
    .theater-info {
      margin-bottom: 1rem;
    }
    .showtimes {
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .time-btn {
      padding: 0.5rem 1rem;
      border: 1px solid #e91e63;
      border-radius: 4px;
      background: white;
      color: #e91e63;
      cursor: pointer;
    }
    .time-btn:hover {
      background: #e91e63;
      color: white;
    }
  `]
})
export class TheatersComponent {
  theaters = [
    {
      id: 1,
      name: 'PVR Cinemas',
      location: 'Downtown Mall',
      showTimes: ['10:00 AM', '1:30 PM', '4:30 PM', '7:30 PM']
    },
    {
      id: 2,
      name: 'INOX',
      location: 'City Center',
      showTimes: ['11:00 AM', '2:30 PM', '5:30 PM', '8:30 PM']
    }
  ];

  constructor(private router: Router) {}

  selectTheater(theater: any) {
    this.router.navigate(['/seats', theater.id]);
  }
}