import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminEventFormComponent } from './admin-event-form.component';

describe('AdminEventFormComponent', () => {
  let component: AdminEventFormComponent;
  let fixture: ComponentFixture<AdminEventFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminEventFormComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminEventFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
