import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="profile-container">
      <div class="profile-card">
        <h2>My Profile</h2>
        
        <div class="profile-info" *ngIf="!isEditing">
          <div class="info-group">
            <label>Name</label>
            <p>{{userData.name}}</p>
          </div>
          <div class="info-group">
            <label>Email</label>
            <p>{{userData.email}}</p>
          </div>
          <div class="info-group">
            <label>Role</label>
            <p>{{userData.role}}</p>
          </div>
          <button class="btn btn-primary" (click)="toggleEdit()">Edit Profile</button>
        </div>

        <form *ngIf="isEditing" (ngSubmit)="saveProfile()" #profileForm="ngForm">
          <div class="form-group">
            <label for="name">Name</label>
            <input type="text" id="name" [(ngModel)]="userData.name" name="name" required class="form-control">
          </div>

          <div class="form-group">
            <label for="email">Email</label>
            <input type="email" id="email" [(ngModel)]="userData.email" name="email" required class="form-control" disabled>
          </div>

          <div class="form-group">
            <label for="currentPassword">Current Password</label>
            <input type="password" id="currentPassword" [(ngModel)]="passwords.current" name="currentPassword" class="form-control">
          </div>

          <div class="form-group">
            <label for="newPassword">New Password</label>
            <input type="password" id="newPassword" [(ngModel)]="passwords.new" name="newPassword" class="form-control">
          </div>

          <div class="form-actions">
            <button type="button" class="btn btn-secondary" (click)="toggleEdit()">Cancel</button>
            <button type="submit" class="btn btn-primary" [disabled]="!profileForm.form.valid">Save Changes</button>
          </div>
        </form>

        <div class="booking-history" *ngIf="bookings.length > 0">
          <h3>Booking History</h3>
          <div class="booking-list">
            <div *ngFor="let booking of bookings" class="booking-item">
              <h4>{{booking.eventName}}</h4>
              <p>Date: {{booking.date}}</p>
              <p>Status: {{booking.status}}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .profile-container {
      padding: 2rem;
      max-width: 800px;
      margin: 0 auto;
    }
    .profile-card {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    .info-group {
      margin-bottom: 1rem;
    }
    .info-group label {
      font-weight: bold;
      color: #666;
    }
    .booking-history {
      margin-top: 2rem;
      padding-top: 2rem;
      border-top: 1px solid #eee;
    }
    .booking-item {
      padding: 1rem;
      border: 1px solid #eee;
      border-radius: 4px;
      margin-bottom: 1rem;
    }
  `]
})
export class UserProfileComponent implements OnInit {
  userData: any = {};
  isEditing = false;
  bookings: any[] = [];
  passwords = {
    current: '',
    new: ''
  };

  constructor(private authService: AuthService) {}

  ngOnInit() {
    this.loadUserData();
    this.loadBookings();
  }

  async loadUserData() {
    const user = this.authService.getCurrentUser();
    if (user) {
      this.userData = { ...user };
    }
  }

  async loadBookings() {
    try {
      const response = await fetch('http://localhost:8080/api/bookings/user', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      if (response.ok) {
        this.bookings = await response.json();
      }
    } catch (error) {
      console.error('Error loading bookings:', error);
    }
  }

  toggleEdit() {
    this.isEditing = !this.isEditing;
    if (!this.isEditing) {
      this.passwords = { current: '', new: '' };
    }
  }

  async saveProfile() {
    try {
      const response = await fetch('http://localhost:8080/api/users/profile', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify({
          name: this.userData.name,
          currentPassword: this.passwords.current,
          newPassword: this.passwords.new
        })
      });

      if (response.ok) {
        this.toggleEdit();
        this.loadUserData();
      }
    } catch (error) {
      console.error('Error updating profile:', error);
    }
  }
}