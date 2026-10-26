export interface Event {
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