import { Routes } from '@angular/router';
import { DashboardPage } from './features/dashboard/dashboard-page';
import { Entry } from './features/entry/app';
import { Login } from './features/login/entry';
import { PetDetails } from './features/pets/details/details';
import { VaccinationEntry } from './features/vaccinations/entry/entry';
import { PetsPage } from './features/pets/entry/entry';
import { SignUp } from './features/signup/entry';
import { authGuard } from './core/auth/auth.guard';
import { AddPet } from './features/pets/add/entry';
import { Workspace } from './shared/layout/workspace';
import { CertificatePage } from './features/vaccinations/certificate/certificate';
import { UpdateRecord } from './features/vaccinations/update/js';

export const routes: Routes = [
  { path: '', component: Entry, pathMatch: 'full' },
  { path: 'login', component: Login },
  {
    path: '',
    component: Workspace,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardPage },
      { path: 'pets/add', component: AddPet },
      { path: 'pets/:petId/vaccinations/add', component: VaccinationEntry },
      { path: 'pets/:petId/vaccinations/:vaccinationId/certificate', component: CertificatePage },
      { path: 'pets/:petId', component: PetDetails },
      { path: 'pets', component: PetsPage },
      { path: 'pets/:petId/vaccinations/update', component: UpdateRecord },
    ],
  },
  { path: 'signin', redirectTo: 'signup', pathMatch: 'full' },
  { path: 'signup', component: SignUp },
  { path: '**', redirectTo: '' },
];
