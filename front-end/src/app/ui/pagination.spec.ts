import { pageItems } from './pagination';

describe('pageItems', () => {
  it('lists every page when there are few', () => {
    expect(pageItems(2, 3)).toEqual([1, 2, 3]);
  });

  it('keeps first, last and the neighbours of the current page, with gaps', () => {
    expect(pageItems(5, 10)).toEqual([1, null, 4, 5, 6, null, 10]);
  });

  it('does not leave a gap between consecutive pages', () => {
    expect(pageItems(1, 10)).toEqual([1, 2, null, 10]);
    expect(pageItems(3, 10)).toEqual([1, 2, 3, 4, null, 10]);
  });

  it('handles a single page', () => {
    expect(pageItems(1, 1)).toEqual([1]);
  });
});
