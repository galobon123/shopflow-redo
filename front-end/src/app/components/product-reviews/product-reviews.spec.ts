import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ProductReviews } from './product-reviews';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { ProductReviews as Reviews, Review } from '../../core/models/review.model';

const api = environment.apiUrl;
const reviewsOf15 = `${api}/products/15/reviews`;

function aReview(overrides: Partial<Review> = {}): Review {
  return { id: 1, rating: 5, comment: 'Muy buena tela', authorName: 'Ana', createdAt: '2026-11-10T13:00:00Z', ...overrides };
}

/** 3 reviews of 5 stars and 2 of 1, like the HU-30 scenario. */
function reviews(overrides: Partial<Reviews> = {}, items: Review[] = [
  aReview({ id: 1 }), aReview({ id: 2, authorName: 'Bruno' }), aReview({ id: 3, authorName: 'Caro' }),
  aReview({ id: 4, rating: 1, comment: 'Se destiñó', authorName: 'Dani' }), aReview({ id: 5, rating: 1, comment: null, authorName: 'Eva' }),
]): Reviews {
  return {
    summary: { average: 3.4, total: 5, counts: { '5': 3, '4': 0, '3': 0, '2': 0, '1': 2 } },
    reviews: { items, page: 0, size: 10, totalItems: items.length, totalPages: 1 },
    canReview: false,
    ...overrides,
  };
}

describe('ProductReviews (HU-30)', () => {
  function render() {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ProductReviews], providers: testProviders() });
    const fixture = TestBed.createComponent(ProductReviews);
    fixture.componentRef.setInput('productId', 15);
    const reviewed = vi.fn();
    fixture.componentInstance.reviewed.subscribe(reviewed);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el); // so focus can be checked
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    /** After a click that starts a request: whenStable would wait for the response. */
    const settle = () => {
      TestBed.tick();
      fixture.detectChanges();
    };
    const button = (text: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(text))!;
    /** The request the panel is waiting for, checked against the expected page and star filter. */
    const listRequest = (params: string) => backend.expectOne((r) => r.url === reviewsOf15 && r.params.toString() === params);
    const items = () => [...el.querySelectorAll('[data-review]')];
    return { fixture, backend, el, update, settle, button, listRequest, items, reviewed };
  }

  it('shows a skeleton while it loads', () => {
    const { el, listRequest } = render();

    expect(el.querySelector('[aria-label="Cargando reseñas"]')).not.toBeNull();
    listRequest('page=0');
  });

  it('shows the average, the count per star and the reviews', async () => {
    const { listRequest, update, items } = render();

    listRequest('page=0').flush(reviews());
    const el = await update();

    expect(el.querySelector('.sf-reviews__average')?.textContent).toContain('3,4');
    expect(el.querySelector('sf-stars')?.getAttribute('aria-label')).toBe('3,4 de 5 estrellas');
    expect(el.textContent).toContain('5 reseñas');
    expect(items()).toHaveLength(5);
    expect(items()[0].textContent).toContain('Ana');
    expect(items()[0].textContent).toContain('Muy buena tela');
    expect(items()[0].querySelector('sf-stars')?.getAttribute('aria-label')).toBe('5 de 5 estrellas');
  });

  it('dates each review by the Argentine day, not the UTC one of the server', async () => {
    const { listRequest, update, items } = render();

    // 23:36 of the 21st in Buenos Aires.
    listRequest('page=0').flush(reviews({}, [aReview({ createdAt: '2026-11-22T02:36:50Z' })]));
    await update();

    expect(items()[0].querySelector('time')?.textContent).toBe('21 de noviembre de 2026');
  });

  it('filters by 1 star and shows only those reviews', async () => {
    const { listRequest, update, settle, button, items } = render();
    listRequest('page=0').flush(reviews());
    await update();

    const oneStar = button('1 estrella');
    expect(oneStar.getAttribute('aria-label')).toBe('1 estrella, 2 reseñas');
    oneStar.click();
    settle();
    expect(button('1 estrella')).toBe(oneStar); // the filters stay while it loads: the focus isn't lost
    listRequest('page=0&rating=1').flush(reviews({}, [
      aReview({ id: 4, rating: 1, comment: 'Se destiñó', authorName: 'Dani' }), aReview({ id: 5, rating: 1, authorName: 'Eva' }),
    ]));
    await update();

    expect(items()).toHaveLength(2);
    expect(items().every((item) => item.querySelector('sf-stars')?.getAttribute('aria-label') === '1 de 5 estrellas')).toBe(true);
    expect(button('1 estrella').getAttribute('aria-pressed')).toBe('true');
    expect(button('Todas').getAttribute('aria-pressed')).toBe('false');
  });

  it('says so when nobody reviewed it yet', async () => {
    const { listRequest, update } = render();
    const none = { average: null, total: 0, counts: { '5': 0, '4': 0, '3': 0, '2': 0, '1': 0 } };

    listRequest('page=0').flush(reviews({ summary: none }, []));
    const el = await update();

    expect(el.textContent).toContain('Todavía nadie reseñó este producto.');
    expect(el.querySelector('.sf-reviews__filters')).toBeNull();
  });

  it('says so when no review has the chosen stars', async () => {
    const { listRequest, update, settle, button } = render();
    listRequest('page=0').flush(reviews());
    await update();

    button('3 estrellas').click();
    settle();
    listRequest('page=0&rating=3').flush(reviews({}, []));
    const el = await update();

    expect(el.textContent).toContain('No hay reseñas de 3 estrellas.');
  });

  it('pages through the reviews', async () => {
    const { listRequest, update, settle, button } = render();
    listRequest('page=0').flush(reviews({ reviews: { items: [aReview()], page: 0, size: 10, totalItems: 12, totalPages: 2 } }));
    const el = await update();

    expect(el.textContent).toContain('Página 1 de 2');
    expect(button('Anterior').getAttribute('aria-disabled')).toBe('true');
    button('Siguiente').click();
    settle();
    listRequest('page=1');
  });

  it('shows the error and retries', async () => {
    const { listRequest, update, settle } = render();

    listRequest('page=0').error(new ProgressEvent('error'), { status: 0 });
    const el = await update();
    expect(el.textContent).toContain('No pudimos conectar con el servidor');

    el.querySelector<HTMLButtonElement>('sf-error-state button')!.click();
    settle();
    listRequest('page=0');
  });

  describe('Calificar', () => {
    async function withForm() {
      const view = render();
      view.listRequest('page=0').flush(reviews({ canReview: true }));
      await view.update();
      const form = view.el.querySelector('form')!;
      const choose = (stars: number) => {
        form.querySelectorAll<HTMLInputElement>('input[type="radio"]')[stars - 1].click();
        view.fixture.detectChanges();
      };
      const write = (comment: string) => {
        const textarea = form.querySelector('textarea')!;
        textarea.value = comment;
        textarea.dispatchEvent(new Event('input'));
      };
      const send = () => {
        form.dispatchEvent(new Event('submit'));
        view.fixture.detectChanges();
      };
      return { ...view, form, choose, write, send };
    }

    it('is not offered to someone who cannot review', async () => {
      const { listRequest, update } = render();
      listRequest('page=0').flush(reviews({ canReview: false }));
      const el = await update();

      expect(el.querySelector('form')).toBeNull();
    });

    it('the stars are a radio group, one radio per star with its own name', async () => {
      const { form } = await withForm();

      const radios = [...form.querySelectorAll<HTMLInputElement>('fieldset input[type="radio"]')];
      expect(form.querySelector('fieldset legend')?.textContent).toContain('Tu calificación');
      expect(radios).toHaveLength(5);
      expect(new Set(radios.map((r) => r.name)).size).toBe(1); // one group: arrows move between them
      expect(radios.map((r) => r.closest('label')?.textContent?.trim())).toEqual([
        '1 estrella', '2 estrellas', '3 estrellas', '4 estrellas', '5 estrellas',
      ]);
    });

    it('sends 4 stars and the comment, then shows the new review and tells the page', async () => {
      const { backend, choose, write, send, listRequest, update, settle, reviewed, el } = await withForm();

      choose(4);
      write('  Muy buena tela  ');
      send();
      const post = backend.expectOne((r) => r.method === 'POST' && r.url === reviewsOf15);
      expect(post.request.body).toEqual({ rating: 4, comment: 'Muy buena tela' });
      post.flush(aReview({ id: 9, rating: 4 }), { status: 201, statusText: 'Created' });
      settle();

      expect(reviewed).toHaveBeenCalled();
      // The form is about to go away: the focus moves to the panel's title instead of falling to the page.
      expect(document.activeElement).toBe(el.querySelector('#reviews-title'));
      listRequest('page=0').flush(reviews({ canReview: false }, [aReview({ id: 9, rating: 4 })]));
      await update();
      expect(el.querySelector('form')).toBeNull();
      expect(el.querySelector('[data-review]')?.textContent).toContain('Muy buena tela');
    });

    it('asks for the stars before sending', async () => {
      const { backend, send, el } = await withForm();

      send();

      backend.expectNone((r) => r.method === 'POST');
      expect(el.textContent).toContain('Elegí de 1 a 5 estrellas.');
    });

    it('shows why the server refused it', async () => {
      const { backend, choose, send, update } = await withForm();

      choose(5);
      send();
      backend.expectOne((r) => r.method === 'POST').flush(
        { status: 403, title: 'Sin permiso', detail: 'Solo podés reseñar productos que recibiste.' },
        { status: 403, statusText: 'Forbidden' },
      );
      const el = await update();

      expect(el.querySelector('form sf-alert')?.textContent).toContain('Solo podés reseñar productos que recibiste.');
    });
  });
});
