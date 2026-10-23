import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UsersApi } from './users-api';

describe('UsersApi.listRequest', () => {
  let api: UsersApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(UsersApi);
  });

  it('sends only pagination when no filter is set', () => {
    expect(api.listRequest({ text: '  ', role: '', status: '', page: 0, size: 20 })).toEqual({
      url: '/api/v1/users',
      params: { page: 0, size: 20 },
    });
  });

  it('adds trimmed text, role and status filters', () => {
    expect(
      api.listRequest({ text: ' ana ', role: 'ANALISTA', status: 'ACTIVE', page: 2, size: 10 }),
    ).toEqual({
      url: '/api/v1/users',
      params: { page: 2, size: 10, text: 'ana', role: 'ANALISTA', status: 'ACTIVE' },
    });
  });
});
