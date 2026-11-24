import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import {
  User,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
} from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { auth, db, functions } from '../lib/firebase';
import { UserProfile } from '../types/admin';

interface AdminAuthContextType {
  currentUser: User | null;
  profile: UserProfile | null;
  isAdmin: boolean;
  isLoading: boolean;
  loginWithCredentials: (idOrEmail: string, password: string) => Promise<UserProfile>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<UserProfile | null>;
}

const AdminAuthContext = createContext<AdminAuthContextType | undefined>(undefined);

export const AdminAuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Fetch Firestore user profile & verify ADMIN role
  const fetchAdminProfile = async (uid: string): Promise<UserProfile | null> => {
    try {
      const userDocRef = doc(db, 'users', uid);
      const userSnap = await getDoc(userDocRef);

      if (!userSnap.exists()) {
        console.warn(`[AdminAuth] User document for ${uid} does not exist in Firestore.`);
        return null;
      }

      const data = userSnap.data();
      const userProfile: UserProfile = {
        uid,
        customId: data.customId || '',
        name: data.name || 'Administrator',
        email: data.email || '',
        role: data.role || 'STUDENT',
        assignedBusId: data.assignedBusId || null,
        active: Boolean(data.active),
      };

      return userProfile;
    } catch (err) {
      console.error('[AdminAuth] Error fetching user profile:', err);
      return null;
    }
  };

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setIsLoading(true);
      if (firebaseUser) {
        setCurrentUser(firebaseUser);
        const p = await fetchAdminProfile(firebaseUser.uid);
        if (p && p.role === 'ADMIN' && p.active) {
          setProfile(p);
        } else {
          // Non-admin session or inactive user
          setProfile(null);
        }
      } else {
        setCurrentUser(null);
        setProfile(null);
      }
      setIsLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const loginWithCredentials = async (idOrEmail: string, password: string): Promise<UserProfile> => {
    const cleanInput = idOrEmail.trim().toLowerCase();
    const cleanPassword = password.trim();

    if (!cleanInput || !cleanPassword) {
      throw new Error('Please provide both Staff ID / Email and password.');
    }

    let authEmail = cleanInput;

    // If input is an ID without '@', resolve via Cloud Function (identical to Kotlin AuthRepository)
    if (!cleanInput.includes('@')) {
      try {
        const resolveFn = httpsCallable<{ adminId: string }, { email: string }>(functions, 'resolveAdminLogin');
        const res = await resolveFn({ adminId: cleanInput });
        if (res.data && res.data.email) {
          authEmail = res.data.email;
        } else {
          authEmail = `${cleanInput}@collegebus.internal`;
        }
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : '';
        if (message.includes('deactivated')) {
          throw new Error('Account is deactivated. Contact Administrator.');
        }
        if (message.includes('ADMIN privileges')) {
          throw new Error('Unauthorized: Account does not have ADMIN privileges.');
        }
        // Fallback for standard internal credential verification
        authEmail = `${cleanInput}@collegebus.internal`;
      }
    }

    // Authenticate with Firebase Auth
    const cred = await signInWithEmailAndPassword(auth, authEmail, cleanPassword);
    const uid = cred.user.uid;

    // Verify role server-side via Firestore
    const userProfile = await fetchAdminProfile(uid);

    if (!userProfile) {
      await signOut(auth);
      throw new Error('Account record not found. Please contact administration.');
    }

    if (userProfile.role !== 'ADMIN') {
      await signOut(auth);
      throw new Error('Access Denied: This portal is restricted to authorized Administrators.');
    }

    if (!userProfile.active) {
      await signOut(auth);
      throw new Error('Account is deactivated. Contact Administrator.');
    }

    setProfile(userProfile);
    setCurrentUser(cred.user);
    return userProfile;
  };

  const logout = async () => {
    await signOut(auth);
    setCurrentUser(null);
    setProfile(null);
  };

  const refreshProfile = async (): Promise<UserProfile | null> => {
    if (!auth.currentUser) return null;
    const p = await fetchAdminProfile(auth.currentUser.uid);
    setProfile(p);
    return p;
  };

  const isAdmin = useMemo(() => {
    return Boolean(profile && profile.role === 'ADMIN' && profile.active);
  }, [profile]);

  return (
    <AdminAuthContext.Provider
      value={{
        currentUser,
        profile,
        isAdmin,
        isLoading,
        loginWithCredentials,
        logout,
        refreshProfile,
      }}
    >
      {children}
    </AdminAuthContext.Provider>
  );
};

export const useAdminAuth = (): AdminAuthContextType => {
  const context = useContext(AdminAuthContext);
  if (!context) {
    throw new Error('useAdminAuth must be used within an AdminAuthProvider');
  }
  return context;
};
