import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-create-event',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './create-event.component.html',
  styleUrls: ['./create-event.component.css']
})
export class CreateEventComponent {
  eventData = {
    name: '',
    description: '',
    date: '',
    time: '',
    venue: '',
    price: 0,
    type: 'movie' // Default type
  };

  eventTypes = ['MOVIE', 'CONCERTS', 'SPORTS'];

  constructor(private router: Router) {}

  async createEvent() {
    try {
      const response = await fetch('http://localhost:8080/api/events', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify(this.eventData)
      });

      if (response.ok) {
        this.router.navigate(['/events']);
      } else {
        console.error('Failed to create event');
      }
    } catch (error) {
      console.error('Error creating event:', error);
    }
  }
}