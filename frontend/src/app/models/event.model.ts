export interface Event {
  id: number;
  name: string;
  description: string;
  category: 'MOVIE' | 'THEATRE' | 'SPORTS' | 'CONCERT';
  venue: string;
  eventDateTime: string;
  price: number;
  vipPrice?: number;
  premiumPrice?: number;
  generalPrice?: number;
  totalSeats: number;
  availableSeats: number;
  analytics?: {
    totalBookings: number;
    totalRevenue: number;
    averageRating: number;
  };
} 