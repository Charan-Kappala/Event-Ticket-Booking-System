import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  userData = {
    name: '',
    email: '',
    password: '',
    role: 'USER', // Default role
    phoneNumber: ''
  };

  confirmPassword: string = '';
  passwordError: string = '';

  roles = [
    { value: 'USER', label: 'Regular User' },
    { value: 'ORGANIZER', label: 'Event Organizer' }
  ];
  errorMessage = '';
  isLoading = false;

  constructor(private router: Router) {}

  validatePasswords() {
    if (this.userData.password && this.confirmPassword) {
      if (this.userData.password !== this.confirmPassword) {
        this.passwordError = 'Passwords do not match';
      } else if (this.userData.password.length < 6) {
        this.passwordError = 'Password must be at least 6 characters long';
      } else {
        this.passwordError = '';
      }
    } else {
      this.passwordError = '';
    }
  }

  async onSubmit() {
    if (!this.userData.name || !this.userData.email || !this.userData.password) {
      this.errorMessage = 'Please fill in all required fields';
      return;
    }

    if (this.userData.password !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    try {
      const response = await fetch('http://localhost:8080/api/users/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': '*/*'
        },
        body: JSON.stringify(this.userData)
      });

      if (response.ok) {
        // Registration successful, redirect to login
        this.router.navigate(['/login']);
      } else {
        const data = await response.json().catch(() => null);
        this.errorMessage = data?.message
          || (data && Object.values(data).join('. '))
          || 'Registration failed. Please try again.';
      }
    } catch (error) {
      console.error('Registration error:', error);
      this.errorMessage = 'An error occurred during registration. Please try again.';
    } finally {
      this.isLoading = false;
    }
  }
}