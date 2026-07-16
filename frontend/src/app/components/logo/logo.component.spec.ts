import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LogoComponent } from './logo.component';

describe('LogoComponent', () => {
  let fixture: ComponentFixture<LogoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [LogoComponent] }).compileComponents();
    fixture = TestBed.createComponent(LogoComponent);
    fixture.detectChanges();
  });

  it('renders the official decorative vector emblem', () => {
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg');

    expect(svg).toBeTruthy();
    expect(svg?.getAttribute('viewBox')).toBe('0 0 96 64');
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
    expect(svg?.getAttribute('focusable')).toBe('false');
    expect(svg?.querySelector('[data-logo-layer="shield"]')).toBeTruthy();
    expect(svg?.querySelector('[data-logo-layer="radar-sweep"]')).toBeTruthy();
  });
});
