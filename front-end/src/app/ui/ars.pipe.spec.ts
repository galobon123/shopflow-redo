import { ArsPipe } from './ars.pipe';

describe('ArsPipe', () => {
  const ars = new ArsPipe();
  const plain = (value: number) => ars.transform(value).replace(/\s/g, ' ');

  it('writes whole pesos without cents and both cent digits otherwise', () => {
    expect(plain(48900)).toBe('$ 48.900');
    expect(plain(62500.5)).toBe('$ 62.500,50');
    expect(plain(7630.25)).toBe('$ 7.630,25');
    expect(ars.transform(null)).toBe('');
  });
});
