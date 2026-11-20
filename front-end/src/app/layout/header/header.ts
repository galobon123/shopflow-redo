import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HeaderActions } from '../header-actions/header-actions';
import { SearchBox } from '../search-box/search-box';

/** Top bar (UI Kit › Header): brand, search, favourites, cart and profile. */
@Component({
  selector: 'app-header',
  imports: [HeaderActions, RouterLink, SearchBox],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.html',
})
export class Header {}
