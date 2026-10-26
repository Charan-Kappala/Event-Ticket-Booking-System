import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  email: string = '';
  password: string = '';
  errorMessage: string = '';
  isLoading: boolean = false;

  constructor(private authService: AuthService, private router: Router) {}

  quickFill(role: 'admin' | 'organizer' | 'user') {
    if (role === 'admin') {
      this.email = 'admin@demo.test';
      this.password = 'demo1234';
    } else if (role === 'organizer') {
      this.email = 'organizer@demo.test';
      this.password = 'demo1234';
    } else {
      this.email = 'alice@demo.test';
      this.password = 'demo1234';
    }
    this.errorMessage = '';
    // Automatically submit to provide seamless 1-click login
    this.onSubmit();
  }

  async onSubmit() {
    if (!this.email || !this.password) {
      this.errorMessage = 'Please enter both email and password';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    try {
      const user = await this.authService.login(this.email, this.password);
      if (!user) {
        this.errorMessage = 'Invalid email or password';
        return;
      }
      switch (user.role) {
        case 'ADMIN':
          await this.router.navigate(['/admin-dashboard']);
          break;
        case 'ORGANIZER':
          await this.router.navigate(['/organizer-dashboard']);
          break;
        default:
          await this.router.navigate(['/shows']);
      }
    } catch (error) {
      console.error('Login error:', error);
      this.errorMessage = 'Could not reach the server. Please try again.';
    } finally {
      this.isLoading = false;
    }
  }
}
