import { FormControl } from '@angular/forms';
import { passwordFits } from './password';

describe('passwordFits', () => {
  it('counts "ñ" twice, like the backend', () => {
    expect(passwordFits(new FormControl('ñ'.repeat(36), { nonNullable: true }))).toBeNull();
    expect(passwordFits(new FormControl('ñ'.repeat(40), { nonNullable: true }))).toEqual({ tooLong: true });
  });
});
