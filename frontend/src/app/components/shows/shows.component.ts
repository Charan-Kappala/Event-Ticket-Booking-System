import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../auth/auth.service';

interface Show {
  id: number;
  name: string;
  description: string;
  eventDateTime: string;
  venue: string;
  totalSeats: number;
  availableSeats: number;
  price: number;
  category: string;
}

@Component({
  selector: 'app-shows',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './shows.component.html',
  styleUrls: ['./shows.component.css']
})
export class ShowsComponent implements OnInit {
  shows: Show[] = [];
  searchTerm: string = '';
  selectedCategory: string = '';
  selectedDate: string = '';
  selectedVenue: string = '';
  venues: string[] = [];
  loading = false;
  error: string | null = null;

  constructor(private router: Router, private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.fetchShows();
  }

  getCategoryImage(category: string): string {
    switch (category) {
      case 'SPORTS':
        return 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800&h=600&fit=crop';
      case 'THEATRE':
        return 'https://images.unsplash.com/photo-1513106580091-1d82408b8cd6?w=800&h=600&fit=crop';
      case 'CONCERT':
        return 'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?w=800&h=600&fit=crop';
      default:
        return 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&h=600&fit=crop';
    }
  }

  logout() {
    this.auth.logout();
  }

  async fetchShows() {
    this.loading = true;
    this.error = null;
    try {
      const response = await this.api.fetch('/api/events');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      this.shows = await response.json();
      // Extract unique venues
      this.venues = [...new Set(this.shows.map(show => show.venue))];
    } catch (error) {
      console.error('Error fetching shows:', error);
      this.error = 'Could not load shows. Please try again.';
    } finally {
      this.loading = false;
    }
  }

  get filteredShows() {
    console.log('Current selectedCategory:', this.selectedCategory);
    console.log('All shows before filtering:', this.shows.map(show => ({ id: show.id, name: show.name, category: show.category })));
    
    const filtered = this.shows
      .filter(show => 
        show.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        show.venue.toLowerCase().includes(this.searchTerm.toLowerCase())
      )
      .filter(show => {
        const categoryMatch = !this.selectedCategory || show.category === this.selectedCategory;
        console.log(`Show "${show.name}" (category: ${show.category}) matches selected category ${this.selectedCategory}:`, categoryMatch);
        return categoryMatch;
      })
      .filter(show => !this.selectedDate || 
        new Date(show.eventDateTime).toDateString() === new Date(this.selectedDate).toDateString()
      )
      .filter(show => !this.selectedVenue || show.venue === this.selectedVenue);
    
    console.log('Filtered shows:', filtered.map(show => ({ id: show.id, name: show.name, category: show.category })));
    return filtered;
  }
}
