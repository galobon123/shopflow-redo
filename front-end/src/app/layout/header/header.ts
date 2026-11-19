import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HeaderActions } from '../header-actions/header-actions';

/** Top bar (UI Kit › Header): brand, search, favourites, cart and profile. */
@Component({
  selector: 'app-header',
  imports: [HeaderActions, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {}
