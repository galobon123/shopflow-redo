import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LucideIconConfig } from 'lucide-angular';
import { Header } from './layout/header/header';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor() {
    // UI Kit › Iconografía: Lucide, 20px, 1.5px stroke.
    const icons = inject(LucideIconConfig);
    icons.size = 20;
    icons.strokeWidth = 1.5;
  }
}
