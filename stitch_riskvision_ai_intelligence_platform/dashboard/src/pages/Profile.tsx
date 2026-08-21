import React, { useState, useEffect } from 'react';
import authApi from '../api/auth';
import { useGithubConnectionStatus, useDisconnectGithub } from '../hooks/useRepository';
import GitHubDisconnectModal from '../components/common/GitHubDisconnectModal';
import type { UserResponse } from '../api/auth';
import GlassCard from '../components/common/GlassCard';
import {
  User as UserIcon,
  Mail,
  Shield,
  Calendar,
  Key,
  AlertCircle,
  RefreshCw,
  Lock,
} from 'lucide-react';

const GoogleIcon = () => (
  <svg className="w-5 h-5 mr-2 shrink-0" viewBox="0 0 24 24" fill="none">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
  </svg>
);

const GithubIcon = () => (
  <svg className="w-5 h-5 mr-2 shrink-0 text-white fill-current" viewBox="0 0 24 24">
    <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.166 6.839 9.489.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.464-1.11-1.464-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.137 20.162 22 16.418 22 12c0-5.523-4.477-10-10-10z" />
  </svg>
);

export const Profile: React.FC = () => {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [fullName, setFullName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [editMode, setEditMode] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Change Password state
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwdSaving, setPwdSaving] = useState(false);
  const [pwdMsg, setPwdMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const fetchProfile = async () => {
    try {
      setLoading(true);
      const data = await authApi.getProfile();
      setUser(data);
      setFullName(data.full_name || '');
      setAvatarUrl(data.avatar_url || '');
    } catch (err: any) {
      console.error('[Profile] Failed to fetch profile details:', err);
      setMessage({ type: 'error', text: 'Failed to synchronize profile metadata.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const updated = await authApi.updateProfile({
        full_name: fullName.trim(),
        avatar_url: avatarUrl.trim(),
      });
      setUser(updated);
      localStorage.setItem('rv_user', JSON.stringify(updated));
      setMessage({ type: 'success', text: 'Profile details updated successfully.' });
      setEditMode(false);
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.detail || 'Failed to update details.' });
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) {
      setPwdMsg({ type: 'error', text: 'New password must be at least 8 characters long.' });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPwdMsg({ type: 'error', text: 'Passwords do not match.' });
      return;
    }

    setPwdSaving(true);
    setPwdMsg(null);

    try {
      await authApi.changePassword(oldPassword, newPassword);
      setPwdMsg({ type: 'success', text: 'Password updated. All other active sessions have been logged out.' });
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setPwdMsg({ type: 'error', text: err.response?.data?.detail || 'Failed to change password.' });
    } finally {
      setPwdSaving(false);
    }
  };

  // GitHub connection & disconnect hooks
  const { data: githubConnectionStatus } = useGithubConnectionStatus();
  const disconnectGithubMutation = useDisconnectGithub();
  const [isGithubDisconnectModalOpen, setIsGithubDisconnectModalOpen] = useState(false);

  const handleConnectProvider = (provider: 'google' | 'github') => {
    const backendUrl = (import.meta as any).env?.VITE_SPRINGBOOT_URL || '';
    const emailParam = user?.email ? `?user_email=${encodeURIComponent(user.email)}` : '';
    window.location.href = `${backendUrl}/oauth2/authorization/${provider}${emailParam}`;
  };

  const handleOpenDisconnectModal = (provider: string) => {
    if (provider === 'github') {
      setIsGithubDisconnectModalOpen(true);
    } else {
      handleDisconnectGoogle();
    }
  };

  const handleConfirmDisconnectGithub = async () => {
    setMessage(null);
    try {
      await disconnectGithubMutation.mutateAsync();
      // Synchronize profile data locally
      if (user && user.connected_accounts) {
        const updatedAccounts = user.connected_accounts.filter((a) => a !== 'github');
        const updatedUser = { ...user, connected_accounts: updatedAccounts };
        setUser(updatedUser);
        localStorage.setItem('rv_user', JSON.stringify(updatedUser));
      }
      setIsGithubDisconnectModalOpen(false);
      setMessage({ type: 'success', text: 'GitHub account disconnected successfully.' });
    } catch (err: any) {
      console.error('[Profile] GitHub disconnect error:', err);
      setMessage({
        type: 'error',
        text: err.response?.data?.message || err.message || 'Failed to disconnect GitHub account.',
      });
    }
  };

  const handleDisconnectGoogle = async () => {
    if (!window.confirm('Are you sure you want to disconnect your linked Google account?')) {
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      const updated = await authApi.disconnectAccount('google');
      setUser(updated);
      localStorage.setItem('rv_user', JSON.stringify(updated));
      setMessage({ type: 'success', text: 'Successfully unlinked Google account.' });
    } catch (err: any) {
      setMessage({
        type: 'error',
        text: err.response?.data?.error || err.response?.data?.detail || 'Failed to unlink Google account.',
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] gap-3 font-sans">
        <RefreshCw className="animate-spin text-cyan-400 h-8 w-8" />
        <span className="text-xs text-slate-400">Synchronizing security profile metadata...</span>
      </div>
    );
  }

  const isProviderConnected = (prov: string) => {
    if (prov === 'github') {
      return githubConnectionStatus?.connected === true;
    }
    return user?.connected_accounts?.includes(prov) || false;
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 font-sans">
      {/* Page Header */}
      <div className="glass-strong rounded-2xl p-6 border border-white/[0.08] flex items-center justify-between shadow-xl">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <UserIcon size={22} className="text-cyan-400" />
            User Settings & Security Profile
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Manage identity metadata, authentication credentials, and connected OAuth providers.
          </p>
        </div>
        <div className="px-3 py-1 rounded-full bg-blue-500/15 border border-blue-500/30 text-cyan-400 font-mono text-xs font-bold">
          SECURE PROFILING
        </div>
      </div>

      {message && (
        <div
          className={`p-4 border rounded-xl text-xs flex items-start gap-2.5 ${
            message.type === 'success'
              ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400'
              : 'bg-red-500/15 border-red-500/30 text-red-400'
          }`}
        >
          <AlertCircle size={16} className="mt-0.5 shrink-0" />
          <span>{message.text}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Profile Card Column */}
        <div className="md:col-span-1 space-y-6">
          <GlassCard className="p-6 text-center space-y-4">
            <div className="relative inline-block mx-auto">
              <div className="w-20 h-20 rounded-2xl bg-gradient-to-tr from-blue-600 to-cyan-400 p-[2px] shadow-[0_0_20px_rgba(56,189,248,0.3)] mx-auto">
                <div className="w-full h-full rounded-[14px] bg-[#0B1220] flex items-center justify-center overflow-hidden">
                  <img
                    src={
                      avatarUrl ||
                      user?.avatar_url ||
                      'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'
                    }
                    alt={user?.full_name || 'User'}
                    className="w-full h-full object-cover"
                  />
                </div>
              </div>
            </div>

            <div>
              <h2 className="text-base font-bold text-white">{user?.full_name || 'User'}</h2>
              <p className="text-xs text-slate-400 mt-0.5 font-mono">@{user?.username}</p>
            </div>

            <div className="pt-3 border-t border-white/[0.06] space-y-2.5 text-xs text-left">
              <div className="flex items-center justify-between text-slate-400">
                <span className="flex items-center gap-1.5"><Shield size={14} /> Role</span>
                <span className="text-cyan-400 font-mono font-bold uppercase">{user?.role}</span>
              </div>
              <div className="flex items-center justify-between text-slate-400">
                <span className="flex items-center gap-1.5"><Mail size={14} /> Email</span>
                <span className="text-slate-200 truncate max-w-[140px] font-mono text-[11px]">{user?.email}</span>
              </div>
              <div className="flex items-center justify-between text-slate-400">
                <span className="flex items-center gap-1.5"><Calendar size={14} /> Joined</span>
                <span className="text-slate-300 font-mono text-[11px]">
                  {user?.created_at ? new Date(user.created_at).toLocaleDateString() : 'N/A'}
                </span>
              </div>
            </div>
          </GlassCard>
        </div>

        {/* Detailed Settings Forms Column */}
        <div className="md:col-span-2 space-y-6">
          {/* Identity Information Card */}
          <GlassCard className="p-6">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-white/[0.06]">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <UserIcon size={16} className="text-cyan-400" />
                Identity Details
              </h3>
              {!editMode && (
                <button
                  onClick={() => setEditMode(true)}
                  className="btn-secondary text-xs py-1 px-3 rounded-lg cursor-pointer"
                >
                  Edit Profile
                </button>
              )}
            </div>

            {editMode ? (
              <form onSubmit={handleUpdateProfile} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">Full Name</label>
                  <input
                    type="text"
                    required
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    className="input-field"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">Avatar Image URL</label>
                  <input
                    type="url"
                    value={avatarUrl}
                    onChange={(e) => setAvatarUrl(e.target.value)}
                    placeholder="https://example.com/avatar.jpg"
                    className="input-field"
                  />
                </div>

                <div className="flex items-center gap-3 pt-2">
                  <button disabled={saving} type="submit" className="btn-primary text-xs py-2 px-4 rounded-xl cursor-pointer">
                    {saving ? <RefreshCw size={14} className="animate-spin" /> : <span>Save Changes</span>}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditMode(false);
                      setFullName(user?.full_name || '');
                      setAvatarUrl(user?.avatar_url || '');
                    }}
                    className="btn-secondary text-xs py-2 px-4 rounded-xl cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <span className="text-slate-400 block mb-1">Full Name</span>
                  <span className="text-slate-100 font-medium">{user?.full_name || 'Not specified'}</span>
                </div>
                <div>
                  <span className="text-slate-400 block mb-1">Username</span>
                  <span className="text-slate-100 font-medium font-mono">@{user?.username}</span>
                </div>
                <div>
                  <span className="text-slate-400 block mb-1">Primary Email</span>
                  <span className="text-slate-100 font-medium font-mono">{user?.email}</span>
                </div>
                <div>
                  <span className="text-slate-400 block mb-1">Primary Login Provider</span>
                  <span className="text-cyan-400 font-mono font-bold uppercase">{user?.provider}</span>
                </div>
              </div>
            )}
          </GlassCard>

          {/* Change Password Card */}
          <GlassCard className="p-6">
            <h3 className="text-sm font-bold text-white flex items-center gap-2 mb-4 pb-3 border-b border-white/[0.06]">
              <Lock size={16} className="text-cyan-400" />
              Change Password
            </h3>

            {pwdMsg && (
              <div
                className={`p-3 border rounded-xl text-xs mb-4 flex items-start gap-2.5 ${
                  pwdMsg.type === 'success'
                    ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400'
                    : 'bg-red-500/15 border-red-500/30 text-red-400'
                }`}
              >
                <AlertCircle size={15} className="mt-0.5 shrink-0" />
                <span>{pwdMsg.text}</span>
              </div>
            )}

            <form onSubmit={handleChangePassword} className="space-y-4">
              <div className="space-y-1.5">
                <label className="block text-xs font-medium text-slate-300">Current Password</label>
                <input
                  type="password"
                  required
                  value={oldPassword}
                  onChange={(e) => setOldPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="input-field"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">New Password</label>
                  <input
                    type="password"
                    required
                    minLength={8}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="input-field"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">Confirm New Password</label>
                  <input
                    type="password"
                    required
                    minLength={8}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="input-field"
                  />
                </div>
              </div>

              <button disabled={pwdSaving} type="submit" className="btn-primary text-xs py-2 px-4 mt-2 rounded-xl cursor-pointer">
                {pwdSaving ? <RefreshCw size={14} className="animate-spin" /> : <span>Update Password</span>}
              </button>
            </form>
          </GlassCard>

          {/* Connected OAuth Providers Card */}
          <GlassCard className="p-6">
            <h3 className="text-sm font-bold text-white flex items-center gap-2 mb-4 pb-3 border-b border-white/[0.06]">
              <Key size={16} className="text-cyan-400" />
              Connected OAuth Providers
            </h3>

            <div className="space-y-3">
              {/* Google */}
              <div className="flex items-center justify-between p-3.5 bg-white/[0.03] border border-white/[0.08] rounded-xl">
                <div className="flex items-center gap-3">
                  <GoogleIcon />
                  <div>
                    <h4 className="text-xs font-bold text-slate-200">Google Account</h4>
                    <p className="text-[11px] text-slate-400 font-mono">
                      {isProviderConnected('google') ? 'Connected and synchronized' : 'Not connected'}
                    </p>
                  </div>
                </div>
                {isProviderConnected('google') ? (
                  <button
                    onClick={() => handleOpenDisconnectModal('google')}
                    className="btn-danger text-xs py-1.5 px-3 rounded-lg cursor-pointer"
                  >
                    Disconnect
                  </button>
                ) : (
                  <button
                    onClick={() => handleConnectProvider('google')}
                    className="btn-secondary text-xs py-1.5 px-3 rounded-lg cursor-pointer"
                  >
                    Connect
                  </button>
                )}
              </div>

              {/* GitHub */}
              <div className="flex items-center justify-between p-3.5 bg-white/[0.03] border border-white/[0.08] rounded-xl">
                <div className="flex items-center gap-3">
                  <GithubIcon />
                  <div>
                    <h4 className="text-xs font-bold text-slate-200">GitHub Account</h4>
                    <p className="text-[11px] text-slate-400 font-mono">
                      {isProviderConnected('github') ? 'Connected and synchronized' : 'Not connected'}
                    </p>
                  </div>
                </div>
                {isProviderConnected('github') ? (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleConnectProvider('github')}
                      className="btn-secondary text-xs py-1.5 px-3 rounded-lg cursor-pointer"
                      title="Connect a different GitHub account"
                    >
                      Switch Account
                    </button>
                    <button
                      onClick={() => handleOpenDisconnectModal('github')}
                      disabled={disconnectGithubMutation.isPending}
                      className="btn-danger text-xs py-1.5 px-3 rounded-lg cursor-pointer"
                    >
                      Disconnect
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => handleConnectProvider('github')}
                    className="btn-secondary text-xs py-1.5 px-3 rounded-lg cursor-pointer"
                  >
                    Connect
                  </button>
                )}
              </div>
            </div>
          </GlassCard>
        </div>
      </div>

      {/* Custom Confirmation Modal for GitHub Disconnection */}
      <GitHubDisconnectModal
        isOpen={isGithubDisconnectModalOpen}
        onClose={() => setIsGithubDisconnectModalOpen(false)}
        onConfirm={handleConfirmDisconnectGithub}
        isPending={disconnectGithubMutation.isPending}
      />
    </div>
  );
};

export default Profile;
