import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Input, OnChanges } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DroneApiService } from '../../services/drone-api.service';

/** Cyberpunk palette shared with the rest of the HUD. */
const CYAN = '#00f3ff';
const RED = '#ff003c';
const AMBER = '#ffb300';

/**
 * "Black Box" analytics panel. Renders a live snapshot of the fleet:
 *  - a doughnut of the SAFE vs THREAT ratio,
 *  - a bar chart of per-unit battery levels,
 *  - and a few raw text stats.
 *
 * Charts recompute purely from the `drones` input via ngOnChanges, so the
 * parent just hands it a fresh array whenever the fleet state changes. The
 * panel also hosts the Mission Debrief PDF export action.
 */
@Component({
  selector: 'app-analytics-panel',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './analytics-panel.component.html',
  styleUrls: ['./analytics-panel.component.scss'],
})
export class AnalyticsPanelComponent implements OnChanges {
  @Input() drones: any[] = [];

  // --- Raw text stats ---
  totalActive = 0;
  avgBattery = 0;
  avgAltitude = 0;

  // --- Mission Debrief export ---
  exporting = false;

  constructor(
    private api: DroneApiService,
    private cdr: ChangeDetectorRef,
  ) {}

  /** Descarcă debrief-ul PDF și îl salvează local ca sentinel-debrief-<timestamp>.pdf. */
  onExport(): void {
    if (this.exporting) return;
    this.exporting = true;

    this.api.exportDebriefPdf().subscribe({
      next: (blob) => {
        const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `sentinel-debrief-${stamp}.pdf`;
        anchor.click();
        URL.revokeObjectURL(url);

        this.exporting = false;
        this.cdr.markForCheck(); // zoneless: repunem butonul în starea normală
      },
      error: (err) => {
        console.warn('⚠️ Debrief export failed:', err.status);
        this.exporting = false;
        this.cdr.markForCheck();
      },
    });
  }

  // --- Doughnut: Threat Ratio (SAFE vs THREAT) ---
  threatChartType: ChartConfiguration<'doughnut'>['type'] = 'doughnut';
  threatChartData: ChartData<'doughnut'> = {
    labels: ['SAFE', 'THREAT'],
    datasets: [
      { data: [0, 0], backgroundColor: [CYAN, RED], borderColor: '#0b101e', borderWidth: 2 },
    ],
  };
  threatChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          color: '#9fb2c8',
          font: { family: 'Courier New, monospace', size: 11 },
          boxWidth: 12,
        },
      },
      tooltip: { bodyColor: '#e0e0e0', titleColor: CYAN },
    },
  };

  // --- Bar: Battery Levels per Call Sign ---
  batteryChartType: ChartConfiguration<'bar'>['type'] = 'bar';
  batteryChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      { data: [], label: 'Battery %', backgroundColor: CYAN, borderRadius: 3, maxBarThickness: 42 },
    ],
  };
  batteryChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: { bodyColor: '#e0e0e0', titleColor: CYAN } },
    scales: {
      x: {
        ticks: { color: '#9fb2c8', font: { family: 'Courier New, monospace', size: 10 } },
        grid: { color: 'rgba(0, 243, 255, 0.08)' },
      },
      y: {
        beginAtZero: true,
        max: 100,
        ticks: {
          color: '#9fb2c8',
          font: { family: 'Courier New, monospace', size: 10 },
          callback: (v) => v + '%',
        },
        grid: { color: 'rgba(0, 243, 255, 0.08)' },
      },
    },
  };

  ngOnChanges(): void {
    this.recompute();
  }

  /** True once we've received any live telemetry for a unit (position/battery/status). */
  private hasTelemetry(d: any): boolean {
    return (
      this.num(d?.battery) !== null ||
      this.num(d?.alt ?? d?.altitude) !== null ||
      this.num(d?.lat) !== null
    );
  }

  private num(v: any): number | null {
    return typeof v === 'number' && !isNaN(v) ? v : null;
  }

  private callSign(d: any): string {
    return (d?.callSign || d?.call_sign || d?.id || 'UNIT').toString().toUpperCase();
  }

  private isThreat(d: any): boolean {
    const status = (d?.threat_level ?? d?.threatLevel ?? d?.status ?? '').toString().toUpperCase();
    return status === 'THREAT';
  }

  private isRtb(d: any): boolean {
    return (d?.status ?? '').toString().toUpperCase() === 'RTB';
  }

  private recompute(): void {
    const fleet = Array.isArray(this.drones) ? this.drones : [];
    // Only units that have actually reported telemetry count as "active".
    const active = fleet.filter((d) => this.hasTelemetry(d));

    // --- Threat ratio (over active units) ---
    const threatCount = active.filter((d) => this.isThreat(d)).length;
    const safeCount = active.length - threatCount;
    this.threatChartData = {
      labels: ['SAFE', 'THREAT'],
      datasets: [
        {
          data: [safeCount, threatCount],
          backgroundColor: [CYAN, RED],
          borderColor: '#0b101e',
          borderWidth: 2,
        },
      ],
    };

    // --- Battery per unit ---
    const labels = active.map((d) => this.callSign(d));
    const batteries = active.map((d) => this.num(d.battery) ?? 0);
    // Unitățile în RTB apar ambru (aterizare autonomă în curs); restul, colorate după nivel.
    const barColors = active.map((d, i) =>
      this.isRtb(d) ? AMBER : batteries[i] < 20 ? RED : batteries[i] < 50 ? AMBER : CYAN,
    );
    this.batteryChartData = {
      labels,
      datasets: [
        {
          data: batteries,
          label: 'Battery %',
          backgroundColor: barColors,
          borderRadius: 3,
          maxBarThickness: 42,
        },
      ],
    };

    // --- Raw text stats ---
    this.totalActive = active.length;
    this.avgBattery = this.mean(active.map((d) => this.num(d.battery)));
    this.avgAltitude = this.mean(active.map((d) => this.num(d.alt ?? d.altitude)));
  }

  /** Mean of the numeric values, ignoring nulls; 0 when there are none. */
  private mean(values: (number | null)[]): number {
    const nums = values.filter((v): v is number => v !== null);
    if (nums.length === 0) return 0;
    return Math.round(nums.reduce((a, b) => a + b, 0) / nums.length);
  }
}
