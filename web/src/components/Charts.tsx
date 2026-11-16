// Small hand-drawn SVG charts. Each draws at the real pixel width of its container, has one y-axis,
// a legend when it has more than one series, and a hover tooltip.

import { useState, type ReactNode } from "react";
import { useWidth } from "../lib/hooks";
import { compact, rupees } from "../lib/money";

export interface Series {
  name: string;
  /** A CSS colour, normally one of the --c-* chart variables. */
  color: string;
  values: (number | null)[];
  dashed?: boolean;
}

const HEIGHT = 220;
const PAD = { top: 12, right: 12, bottom: 24, left: 48 };

/** A round-number y-axis that covers [min, max]. */
function scale(min: number, max: number) {
  const span = max - min || 1;
  const rough = span / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * power).find((s) => s >= rough) ?? rough;
  const low = Math.floor(min / step) * step;
  const high = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let v = low; v <= high + step / 2; v += step) ticks.push(v);
  return { low, high: high === low ? low + step : high, ticks };
}

export function Legend({ series }: { series: Series[] }) {
  return (
    <div className="legend">
      {series.map((s) => (
        <span key={s.name}>
          <i style={{ background: s.dashed ? "transparent" : s.color, borderColor: s.color }} className={s.dashed ? "dash" : ""} />
          {s.name}
        </span>
      ))}
    </div>
  );
}

function Tooltip({ x, width, title, rows }: { x: number; width: number; title: string; rows: { name: string; color: string; value: number | null }[] }) {
  const onLeft = x > width / 2;
  return (
    <div className="tooltip" style={onLeft ? { right: width - x + 12 } : { left: x + 12 }}>
      <b>{title}</b>
      {rows.filter((r) => r.value !== null).map((r) => (
        <div key={r.name}>
          <i style={{ background: r.color }} />
          {r.name}
          <span>{rupees(r.value!)}</span>
        </div>
      ))}
    </div>
  );
}

function Frame({ width, ticks, y, children }: { width: number; ticks: number[]; y: (v: number) => number; children: ReactNode }) {
  return (
    <>
      {ticks.map((tick) => (
        <g key={tick}>
          <line x1={PAD.left} x2={width - PAD.right} y1={y(tick)} y2={y(tick)} className={tick === 0 ? "axis" : "gridline"} />
          <text x={PAD.left - 6} y={y(tick) + 4} textAnchor="end" className="tick">{compact(tick)}</text>
        </g>
      ))}
      {children}
    </>
  );
}

/** Which labels to print so they never collide: about one per 44px. */
const labelEvery = (count: number, plotWidth: number) => Math.max(1, Math.ceil(count / Math.max(plotWidth / 44, 1)));

/** Bars side by side per label, for comparing a few measures over time. */
export function GroupedBars({ labels, series, onPick, titles }: { labels: string[]; series: Series[]; onPick?: (index: number) => void; titles?: string[] }) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const all = series.flatMap((s) => s.values.map((v) => v ?? 0));
  const { low, high, ticks } = scale(Math.min(0, ...all), Math.max(1, ...all));
  const plotWidth = Math.max(width - PAD.left - PAD.right, 1);
  const band = plotWidth / Math.max(labels.length, 1);
  const barWidth = Math.max(Math.min((band * 0.72) / series.length - 2, 24), 1.5);
  const y = (v: number) => PAD.top + (HEIGHT - PAD.top - PAD.bottom) * (1 - (v - low) / (high - low));
  const every = labelEvery(labels.length, plotWidth);
  return (
    <div className="chart" ref={ref} onMouseLeave={() => setHover(null)}>
      {series.length > 1 && <Legend series={series} />}
      {width > 0 && (
        <svg width={width} height={HEIGHT} role="img" aria-label={series.map((s) => s.name).join(", ") + " by period"}>
          <Frame width={width} ticks={ticks} y={y}>
            {labels.map((label, i) => {
              const centre = PAD.left + band * (i + 0.5);
              const groupWidth = series.length * (barWidth + 2) - 2;
              return (
                <g key={i} onMouseEnter={() => setHover(i)} onClick={() => onPick?.(i)} style={onPick ? { cursor: "pointer" } : undefined}>
                  <rect x={PAD.left + band * i} y={PAD.top} width={band} height={HEIGHT - PAD.top - PAD.bottom} className={hover === i ? "band on" : "band"} />
                  {series.map((s, k) => {
                    const value = s.values[i] ?? 0;
                    const top = Math.min(y(value), y(0));
                    const height = Math.abs(y(value) - y(0));
                    return <rect key={s.name} x={centre - groupWidth / 2 + k * (barWidth + 2)} y={top} width={barWidth} height={height} rx={Math.min(3, barWidth / 2, height)} fill={s.color} />;
                  })}
                  {i % every === 0 && <text x={centre} y={HEIGHT - 6} textAnchor="middle" className="tick">{label}</text>}
                </g>
              );
            })}
          </Frame>
        </svg>
      )}
      {hover !== null && (
        <Tooltip x={PAD.left + band * (hover + 0.5)} width={width} title={titles?.[hover] ?? labels[hover]} rows={series.map((s) => ({ name: s.name, color: s.color, value: s.values[hover] }))} />
      )}
    </div>
  );
}

/** Lines over the same x positions, with a crosshair. A null value breaks the line. */
export function Lines({ labels, series, titles }: { labels: string[]; series: Series[]; titles?: string[] }) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const all = series.flatMap((s) => s.values.filter((v): v is number => v !== null));
  const { low, high, ticks } = scale(Math.min(0, ...all), Math.max(1, ...all));
  const plotWidth = Math.max(width - PAD.left - PAD.right, 1);
  const x = (i: number) => PAD.left + (labels.length <= 1 ? plotWidth / 2 : (plotWidth * i) / (labels.length - 1));
  const y = (v: number) => PAD.top + (HEIGHT - PAD.top - PAD.bottom) * (1 - (v - low) / (high - low));
  const every = labelEvery(labels.length, plotWidth);
  const path = (values: (number | null)[]) => {
    let d = "";
    let pen = false;
    values.forEach((v, i) => {
      if (v === null) pen = false;
      else {
        d += `${pen ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`;
        pen = true;
      }
    });
    return d;
  };
  const onMove = (event: React.MouseEvent<SVGSVGElement>) => {
    const offset = event.clientX - event.currentTarget.getBoundingClientRect().left - PAD.left;
    setHover(Math.min(Math.max(Math.round((offset / plotWidth) * (labels.length - 1)), 0), labels.length - 1));
  };
  return (
    <div className="chart" ref={ref} onMouseLeave={() => setHover(null)}>
      {series.length > 1 && <Legend series={series} />}
      {width > 0 && (
        <svg width={width} height={HEIGHT} onMouseMove={onMove} role="img" aria-label={series.map((s) => s.name).join(", ") + " over time"}>
          <Frame width={width} ticks={ticks} y={y}>
            {labels.map((label, i) => i % every === 0 && <text key={i} x={x(i)} y={HEIGHT - 6} textAnchor="middle" className="tick">{label}</text>)}
            {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={PAD.top} y2={HEIGHT - PAD.bottom} className="crosshair" />}
            {series.map((s) => (
              <path key={s.name} d={path(s.values)} fill="none" stroke={s.color} strokeWidth={2} strokeDasharray={s.dashed ? "5 4" : undefined} strokeLinejoin="round" strokeLinecap="round" />
            ))}
            {hover !== null && series.map((s) => s.values[hover] !== null && s.values[hover] !== undefined && (
              <circle key={s.name} cx={x(hover)} cy={y(s.values[hover]!)} r={4} fill={s.color} className="dot" />
            ))}
          </Frame>
        </svg>
      )}
      {hover !== null && <Tooltip x={x(hover)} width={width} title={titles?.[hover] ?? labels[hover]} rows={series.map((s) => ({ name: s.name, color: s.color, value: s.values[hover] ?? null }))} />}
    </div>
  );
}

/** A meter: how much of [total] is used, with an optional tick (today's pace). Overspend turns it critical. */
export function Meter({ value, total, tick, color }: { value: number; total: number; tick?: number; color?: string }) {
  const fraction = total <= 0 ? (value > 0 ? 1 : 0) : Math.min(Math.max(value / total, 0), 1);
  const over = value > total;
  return (
    <div className={over ? "meter over" : "meter"} role="img" aria-label={`${rupees(value)} of ${rupees(total)}`}>
      <div style={{ width: `${fraction * 100}%`, background: over ? undefined : color }} />
      {tick !== undefined && tick > 0 && tick < 1 && <span style={{ left: `${tick * 100}%` }} />}
    </div>
  );
}
