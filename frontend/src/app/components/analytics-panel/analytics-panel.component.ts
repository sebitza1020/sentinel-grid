import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

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
 * It is a dumb/presentational component: it recomputes everything from the
 * `drones` input via ngOnChanges, so the parent just has to hand it a fresh
 * array whenever the fleet state changes.
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

  // --- Doughnut: Threat Ratio (SAFE vs THREAT) ---
  threatChartType: ChartConfiguration<'doughnut'>['type'] = 'doughnut';
  threatChartData: ChartData<'doughnut'> = {
    labels: ['SAFE', 'THREAT'],
    datasets: [{ data: [0, 0], backgroundColor: [CYAN, RED], borderColor: '#0b101e', borderWidth: 2 }],
  };
  threatChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color: '#9fb2c8', font: { family: 'Courier New, monospace', size: 11 }, boxWidth: 12 },
      },
      tooltip: { bodyColor: '#e0e0e0', titleColor: CYAN },
    },
  };

  // --- Bar: Battery Levels per Call Sign ---
  batteryChartType: ChartConfiguration<'bar'>['type'] = 'bar';
  batteryChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [{ data: [], label: 'Battery %', backgroundColor: CYAN, borderRadius: 3, maxBarThickness: 42 }],
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
        ticks: { color: '#9fb2c8', font: { family: 'Courier New, monospace', size: 10 }, callback: (v) => v + '%' },
        grid: { color: 'rgba(0, 243, 255, 0.08)' },
      },
    },
  };

  ngOnChanges(): void {
    this.recompute();
  }

  /** True once we've received any live telemetry for a unit (position/battery/status). */
  private hasTelemetry(d: any): boolean {
    return this.num(d?.battery) !== null || this.num(d?.alt ?? d?.altitude) !== null || this.num(d?.lat) !== null;
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
        { data: [safeCount, threatCount], backgroundColor: [CYAN, RED], borderColor: '#0b101e', borderWidth: 2 },
      ],
    };

    // --- Battery per unit ---
    const labels = active.map((d) => this.callSign(d));
    const batteries = active.map((d) => this.num(d.battery) ?? 0);
    this.batteryChartData = {
      labels,
      datasets: [
        {
          data: batteries,
          label: 'Battery %',
          backgroundColor: batteries.map((b) => (b < 20 ? RED : b < 50 ? AMBER : CYAN)),
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
